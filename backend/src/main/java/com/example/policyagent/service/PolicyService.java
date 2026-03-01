package com.example.policyagent.service;

import com.example.policyagent.entity.*;
import com.example.policyagent.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PolicyService {

    private static final Logger logger = LoggerFactory.getLogger(PolicyService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RegulatoryUpdateRepository updateRepository;
    private final RequirementRepository requirementRepository;
    private final GapRepository gapRepository;
    private final AuditLogRepository auditRepository;
    private final PolicyDraftRepository policyDraftRepository;
    private final CodeSpecificationRepository codeSpecificationRepository;

    public PolicyService(ChatClient chatClient,
            RegulatoryUpdateRepository updateRepository,
            RequirementRepository requirementRepository,
            GapRepository gapRepository,
            AuditLogRepository auditRepository,
            PolicyDraftRepository policyDraftRepository,
            CodeSpecificationRepository codeSpecificationRepository) {

        this.chatClient = chatClient;
        this.updateRepository = updateRepository;
        this.requirementRepository = requirementRepository;
        this.gapRepository = gapRepository;
        this.auditRepository = auditRepository;
        this.policyDraftRepository = policyDraftRepository;
        this.codeSpecificationRepository = codeSpecificationRepository;
    }

    // ================= CACHE =================

    private static final int CACHE_MAX_SIZE = 10;

    private final Map<String, CachedAnalysis> analysisCache = java.util.Collections.synchronizedMap(
            new LinkedHashMap<String, CachedAnalysis>(CACHE_MAX_SIZE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedAnalysis> eldest) {
                    return size() > CACHE_MAX_SIZE;
                }
            });

    private static class CachedAnalysis {
        final JsonNode parsed;
        final long timestamp;

        CachedAnalysis(JsonNode parsed) {
            this.parsed = parsed;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private String createCacheKey(String existingPolicy, String newRegulation) {
        return Integer.toHexString((existingPolicy + "||" + newRegulation).hashCode());
    }

    // ================= MAIN ANALYSIS =================

    @Transactional
    public JsonNode analyzeAndParse(String existingPolicy, String newRegulation) {

        String cacheKey = createCacheKey(existingPolicy, newRegulation);
        logger.info("=== analyzeAndParse() START === cache key: {}", cacheKey);

        synchronized (analysisCache) {
            CachedAnalysis cached = analysisCache.get(cacheKey);
            if (cached != null) {
                logger.info("Cache HIT - returning cached analysis");
                return cached.parsed;
            }
        }

        logger.info("Cache MISS - calling LLM for new analysis");

        String raw = analyze(existingPolicy, newRegulation);
        logger.info("Raw LLM response length: {} characters", raw.length());

        JsonNode parsed = safeParse(raw);

        if (parsed != null && !parsed.has("error")) {
            logger.info("JSON parsed successfully, attempting to persist analysis");
            Long savedId = persistAnalysis(parsed, newRegulation);
            logger.info("Analysis persisted successfully with regulatory update ID: {}", savedId);
        } else {
            logger.error("JSON parsing failed or returned error - analysis will NOT be persisted");
        }

        synchronized (analysisCache) {
            analysisCache.put(cacheKey, new CachedAnalysis(parsed));
        }

        logger.info("=== analyzeAndParse() END ===");
        return parsed;
    }

    public String analyze(String existingPolicy, String newRegulation) {
        logger.info("=== analyze() START === calling LLM");

        String prompt = String.format("""
                You are the Policy-as-Code Regulatory Change Agent.

                IMPORTANT:
                - Return ONLY raw JSON.
                - Do NOT add explanations.
                - Do NOT use markdown.
                - Do NOT wrap in triple backticks.
                - Output must start with { and end with }.

                Return JSON exactly in this structure:

                {
                  "agentName": "Policy-as-Code Regulatory Change Agent",
                  "changeDetected": true,
                  "confidenceScore": 0.0,
                  "requirements": [
                    {
                      "id": 1,
                      "text": "",
                      "type": "policy|control|system",
                      "tests": [],
                      "satisfied": false,
                      "rationale": "",
                      "recommendation": "no_action|update_policy|add_control|implement_system_rule"
                    }
                  ],
                  "summary": {
                    "totalRequirements": 0,
                    "alreadySatisfied": 0,
                    "policyUpdatesNeeded": 0,
                    "newControlsNeeded": 0,
                    "systemImplementationsNeeded": 0
                  },
                  "gapReport": [],
                  "policyDrafts": [],
                  "codeSpecifications": []
                }

                Existing Policy:
                %s

                New Regulation:
                %s
                """, existingPolicy, newRegulation);

        String raw = chatClient.call(new Prompt(prompt))
                .getResult()
                .getOutput()
                .getContent();

        logger.debug("Raw LLM response (first 500 chars): {}",
                raw.length() > 500 ? raw.substring(0, 500) + "..." : raw);

        logger.info("=== analyze() END === response length: {}", raw.length());
        return raw;
    }

    private JsonNode safeParse(String json) {
        logger.info("=== safeParse() START ===");
        logger.debug("Raw JSON input (first 300 chars): {}",
                json.length() > 300 ? json.substring(0, 300) + "..." : json);

        try {
            // Defensive: strip markdown code fences if present
            String cleanJson = json;
            if (cleanJson.contains("```json")) {
                logger.info("Detected markdown code fence in response - extracting pure JSON");
                int start = cleanJson.indexOf("```json") + 7; // length of "```json"
                int end = cleanJson.indexOf("```", start);
                if (end > start) {
                    cleanJson = cleanJson.substring(start, end).trim();
                    logger.debug("Extracted JSON from markdown, length: {}", cleanJson.length());
                }
            } else if (cleanJson.contains("```")) {
                logger.info("Detected generic code fence in response - extracting pure JSON");
                int start = cleanJson.indexOf("```") + 3;
                int end = cleanJson.indexOf("```", start);
                if (end > start) {
                    cleanJson = cleanJson.substring(start, end).trim();
                    logger.debug("Extracted JSON from generic fence, length: {}", cleanJson.length());
                }
            }

            // Find first { and last } to ensure we parse valid JSON
            int jsonStart = cleanJson.indexOf('{');
            int jsonEnd = cleanJson.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                cleanJson = cleanJson.substring(jsonStart, jsonEnd + 1);
                logger.debug("Extracted JSON bounds from {} to }, length: {}", jsonStart, cleanJson.length());
            }

            JsonNode parsed = objectMapper.readTree(cleanJson);
            logger.info("JSON parsing succeeded - root has {} fields", parsed.size());
            logger.debug("Parsed JSON keys: {}", (Iterable<String>) () -> parsed.fieldNames());
            logger.info("=== safeParse() END - SUCCESS ===");
            return parsed;

        } catch (Exception e) {
            logger.error("JSON parsing FAILED - Exception: {} - Message: {}",
                    e.getClass().getSimpleName(), e.getMessage());
            logger.debug("Stack trace: ", e);

            ObjectNode err = objectMapper.createObjectNode();
            err.put("error", "failed_to_parse_response");
            err.put("message", e.getMessage());
            err.put("originalLength", json.length());

            logger.info("=== safeParse() END - ERROR OBJECT CREATED ===");
            return err;
        }
    }

    // ================= PERSISTENCE =================

    @Transactional
    private Long persistAnalysis(JsonNode root, String regulationText) {

        logger.info("=== persistAnalysis() START ===");

        try {

            // ==========================
            // 1. SAVE REGULATORY UPDATE
            // ==========================

            RegulatoryUpdate update = new RegulatoryUpdate();
            update.setTitle("Manual Submission");
            update.setAuthority("Manual Input");
            update.setFullText(regulationText);
            update.setStatus(RegulatoryStatus.ANALYZED);
            update.setPublicationDate(Instant.now());
            update.setConfidenceScore(root.path("confidenceScore").asDouble(0.75));

            update = updateRepository.save(update);
            logger.info("RegulatoryUpdate saved with ID: {}", update.getId());

            // ==========================
            // 2. SAVE REQUIREMENTS
            // ==========================

            JsonNode requirementsNode = root.path("requirements");
            JsonNode gapReportNode = root.path("gapReport");
            JsonNode draftsNode = root.path("policyDrafts");
            JsonNode specsNode = root.path("codeSpecifications");

            int reqCount = 0;

            if (requirementsNode.isArray()) {

                for (JsonNode r : requirementsNode) {

                    RequirementEntity req = new RequirementEntity();
                    req.setRegulatoryUpdateId(update.getId());
                    req.setText(r.path("text").asText());
                    req.setType(r.path("type").asText());
                    req.setSatisfied(r.path("satisfied").asBoolean());

                    String recommendation = r.path("recommendation").asText();
                    if (recommendation == null || recommendation.isBlank()) {
                        recommendation = "no_action";
                    }
                    String text = r.path("text").asText().toLowerCase();
                    String type = r.path("type").asText().toLowerCase();

                    // Enterprise override logic
                    boolean looksLikeRule = text.contains("exceeds")
                            || text.contains("greater than")
                            || text.contains("less than")
                            || text.contains("if ")
                            || text.contains(" and ")
                            || text.matches(".*\\d+.*"); // contains numbers

                    if (("add_control".equalsIgnoreCase(recommendation) && looksLikeRule)
                            || text.contains("automatically")
                            || text.contains("system")) {

                        recommendation = "implement_system_rule";
                        logger.info("Backend override triggered → implement_system_rule");
                    }

                    req.setRecommendation(recommendation);

                    req = requirementRepository.save(req);
                    reqCount++;

                    logger.info("Requirement saved ID: {}", req.getId());

                    boolean gapCreatedFromLLM = false;

                    // ==========================
                    // 3. GAP HANDLING
                    // ==========================

                    if (gapReportNode.isArray()) {

                        for (JsonNode g : gapReportNode) {

                            if (g.path("requirementId").asLong() == r.path("id").asLong()) {

                                GapEntity gap = new GapEntity();
                                gap.setRequirementId(req.getId());
                                gap.setIssue(g.path("issue").asText("Regulatory gap identified"));
                                gap.setDetail(g.path("detail").asText("Gap detected based on LLM analysis."));
                                gapRepository.save(gap);

                                gapCreatedFromLLM = true;

                                logger.info("Gap saved from LLM for requirement ID: {}", req.getId());
                            }
                        }
                    }

                    // Backend enforced gap
                    if (!req.isSatisfied() && !gapCreatedFromLLM) {

                        GapEntity autoGap = new GapEntity();
                        autoGap.setRequirementId(req.getId());
                        autoGap.setIssue("Requirement not satisfied");
                        autoGap.setDetail(
                                "Backend-enforced gap: Requirement marked as unsatisfied but no gap was provided by LLM. "
                                        + "Recommendation: " + req.getRecommendation());

                        gapRepository.save(autoGap);

                        logger.info("Auto-generated gap for requirement ID: {}", req.getId());
                    }

                    // ==========================
                    // 4. POLICY DRAFT ENFORCEMENT
                    // ==========================

                    if ("update_policy".equalsIgnoreCase(req.getRecommendation())) {

                        boolean draftCreated = false;

                        if (draftsNode.isArray()) {

                            for (JsonNode d : draftsNode) {

                                if (d.path("requirementId").asLong() == r.path("id").asLong()) {

                                    PolicyDraftEntity draft = new PolicyDraftEntity();
                                    draft.setRequirementId(req.getId());
                                    draft.setDraft(d.path("draft").asText());
                                    policyDraftRepository.save(draft);

                                    draftCreated = true;
                                    logger.info("Policy draft saved from LLM for requirement ID: {}", req.getId());
                                }
                            }
                        }

                        if (!draftCreated) {

                            PolicyDraftEntity autoDraft = new PolicyDraftEntity();
                            autoDraft.setRequirementId(req.getId());
                            autoDraft.setDraft(
                                    "Auto-generated draft: Update policy to comply with requirement: "
                                            + req.getText());

                            policyDraftRepository.save(autoDraft);

                            logger.info("Auto-generated policy draft for requirement ID: {}", req.getId());
                        }
                    }

                    // ==========================
                    // 5. CODE SPEC ENFORCEMENT
                    // ==========================

                    if ("implement_system_rule".equalsIgnoreCase(req.getRecommendation())) {

                        boolean specCreated = false;

                        if (specsNode.isArray()) {

                            for (JsonNode s : specsNode) {

                                if (s.path("requirementId").asLong() == r.path("id").asLong()) {

                                    CodeSpecificationEntity spec = new CodeSpecificationEntity();
                                    spec.setRequirementId(req.getId());
                                    spec.setSpecification(s.path("spec").asText());
                                    codeSpecificationRepository.save(spec);

                                    specCreated = true;

                                    logger.info("Code spec saved from LLM for requirement ID: {}", req.getId());
                                }
                            }
                        }

                        if (!specCreated) {

                            CodeSpecificationEntity autoSpec = new CodeSpecificationEntity();
                            autoSpec.setRequirementId(req.getId());
                            autoSpec.setSpecification(
                                    "Auto-generated system rule: IF condition related to '"
                                            + req.getText()
                                            + "' THEN enforce compliance.");

                            codeSpecificationRepository.save(autoSpec);

                            logger.info("Auto-generated code specification for requirement ID: {}", req.getId());
                        }
                    }
                }
            }

            // ==========================
            // 6. AUDIT LOG
            // ==========================

            AuditLog log = new AuditLog();
            log.setAction("ANALYSIS_COMPLETED");
            log.setActor("SYSTEM");
            log.setDetails("Analysis persisted with " + reqCount + " requirements");
            log.setTimestamp(Instant.now());

            auditRepository.save(log);

            logger.info("=== persistAnalysis() END SUCCESS ===");
            return update.getId();

        } catch (Exception e) {

            logger.error("persistAnalysis() FAILED", e);
            throw new RuntimeException("Failed to persist regulatory analysis", e);
        }
    }

    // ================= SUB ENDPOINTS =================

    public JsonNode getRequirements(String existingPolicy, String newRegulation) {
        return analyzeAndParse(existingPolicy, newRegulation).path("requirements");
    }

    public JsonNode getGapReport(String existingPolicy, String newRegulation) {
        return analyzeAndParse(existingPolicy, newRegulation).path("gapReport");
    }

    public JsonNode getPolicyDrafts(String existingPolicy, String newRegulation) {
        return analyzeAndParse(existingPolicy, newRegulation).path("policyDrafts");
    }

    public JsonNode getCodeSpecifications(String existingPolicy, String newRegulation) {
        return analyzeAndParse(existingPolicy, newRegulation).path("codeSpecifications");
    }

    public JsonNode getSummary(String existingPolicy, String newRegulation) {
        return analyzeAndParse(existingPolicy, newRegulation).path("summary");
    }
}