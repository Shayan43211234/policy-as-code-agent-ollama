package com.example.policyagent.service;

import com.example.policyagent.entity.*;
import com.example.policyagent.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

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

    // ================= MAIN ANALYSIS =================

    @Transactional
    public JsonNode analyzeAndParse(String existingPolicy, String newRegulation, String sourceLink) {

        logger.info("Cache MISS - calling LLM for new analysis");

        // RSS duplicate protection (manual analysis allowed)
        if (sourceLink != null && updateRepository.existsBySourceLink(sourceLink)) {

            logger.info("Duplicate feed detected. Returning existing analysis for: {}", sourceLink);

            RegulatoryUpdate existing = updateRepository.findBySourceLink(sourceLink).orElse(null);

            if (existing != null) {

                ObjectNode result = objectMapper.createObjectNode();

                result.put("agentName", "Policy-as-Code Regulatory Change Agent");
                result.put("changeDetected", true);

                result.put("confidenceScore", existing.getConfidenceScore());

                result.set("requirements", objectMapper.createArrayNode());
                result.set("gapReport", objectMapper.createArrayNode());
                result.set("policyDrafts", objectMapper.createArrayNode());
                result.set("codeSpecifications", objectMapper.createArrayNode());

                result.set("summary", objectMapper.createObjectNode());

                return result;
            }

        }

        String raw = analyze(existingPolicy, newRegulation);
        logger.info("Raw LLM response length: {}", raw.length());

        JsonNode parsed = safeParse(raw);

        if (parsed != null && !parsed.has("error")) {
            Long savedId = persistAnalysis(parsed, newRegulation, sourceLink);
            logger.info("Analysis persisted successfully with regulatory update ID: {}", savedId);
        }

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
                - Be precise and deterministic.

                COMPARISON RULES:
                - Carefully compare numerical values (days, amounts, thresholds).
                - Carefully compare frequencies (e.g., 180 days vs 180 days).
                - If the existing policy already satisfies the regulation requirement,
                  then set:
                      "satisfied": true
                      "recommendation": "no_action"
                - Only mark "satisfied": false if there is a real gap.
                - Do NOT create artificial gaps.
                - If values are equal, it is satisfied.

                The bank has the following business lines:
                - Retail Banking
                - Commercial Lending
                - Wealth Management
                - Treasury

                The bank has the following systems:
                - Vendor Risk System
                - Core Banking Platform
                - AML Monitoring Engine
                - Payments Gateway

                For EACH requirement:
                - Determine if it is already satisfied by the Existing Policy.
                - Determine which ONE business line is primarily impacted.
                - Determine which ONE system is primarily impacted.

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
                      "recommendation": "no_action|update_policy|add_control|implement_system_rule",
                      "impactedBusinessLine": "",
                      "impactedSystem": ""
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
    private Long persistAnalysis(JsonNode root, String regulationText, String sourceLink) {

        if (sourceLink != null && updateRepository.existsBySourceLink(sourceLink)) {
            logger.info("Duplicate feed ignored during persistence: {}", sourceLink);
            return null;
        }

        ArrayNode gapArray = objectMapper.createArrayNode();
        ArrayNode draftArray = objectMapper.createArrayNode();
        ArrayNode specArray = objectMapper.createArrayNode();

        logger.info("=== persistAnalysis() START ===");

        try {

            // ==========================
            // 1. SAVE REGULATORY UPDATE
            // ==========================

            RegulatoryUpdate update = new RegulatoryUpdate();

            if (sourceLink != null) {
                update.setTitle("Regulatory Feed");
                update.setAuthority("External Feed");
                update.setSourceLink(sourceLink);
            } else {
                update.setTitle("Manual Submission");
                update.setAuthority("Manual Input");
            }

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

                    String recommendation = r.path("recommendation").asText("no_action");
                    req.setRecommendation(recommendation);

                    String impactedBusinessLine = r.path("impactedBusinessLine").asText(null);
                    String impactedSystem = r.path("impactedSystem").asText(null);

                    req.setImpactedBusinessLine(
                            impactedBusinessLine != null && !impactedBusinessLine.isBlank()
                                    ? impactedBusinessLine
                                    : "Unknown");

                    req.setImpactedSystem(
                            impactedSystem != null && !impactedSystem.isBlank()
                                    ? impactedSystem
                                    : "Unknown");

                    req = requirementRepository.save(req);

                    // expose DB id to frontend
                    ((ObjectNode) r).put("dbId", req.getId());

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

                                ObjectNode gapJson = objectMapper.createObjectNode();
                                gapJson.put("requirementId", req.getId());
                                gapJson.put("issue", gap.getIssue());
                                gapJson.put("detail", gap.getDetail());

                                gapArray.add(gapJson);

                                logger.info("Gap saved from LLM for requirement ID: {}", req.getId());
                            }
                        }
                    }

                    // Backend enforced gap
                    if (!req.getSatisfied() && !gapCreatedFromLLM) {

                        GapEntity autoGap = new GapEntity();
                        autoGap.setRequirementId(req.getId());
                        autoGap.setIssue("Requirement not satisfied");
                        autoGap.setDetail(
                                "Backend-enforced gap: Requirement marked as unsatisfied but no gap was provided by LLM. "
                                        + "Recommendation: " + req.getRecommendation());

                        gapRepository.save(autoGap);

                        ObjectNode gapJson = objectMapper.createObjectNode();
                        gapJson.put("requirementId", req.getId());
                        gapJson.put("issue", autoGap.getIssue());
                        gapJson.put("detail", autoGap.getDetail());

                        gapArray.add(gapJson);

                        logger.info("Auto-generated gap for requirement ID: {}", req.getId());
                    }

                    // ==========================
                    // 4. POLICY DRAFT
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

                                    ObjectNode draftJson = objectMapper.createObjectNode();
                                    draftJson.put("requirementId", req.getId());
                                    draftJson.put("draft", draft.getDraft());

                                    draftArray.add(draftJson);

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

                            ObjectNode draftJson = objectMapper.createObjectNode();
                            draftJson.put("requirementId", req.getId());
                            draftJson.put("draft", autoDraft.getDraft());

                            draftArray.add(draftJson);

                            logger.info("Auto-generated policy draft for requirement ID: {}", req.getId());
                        }
                    }

                    // ==========================
                    // 5. CODE SPEC
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

                                    ObjectNode specJson = objectMapper.createObjectNode();
                                    specJson.put("requirementId", req.getId());
                                    specJson.put("spec", spec.getSpecification());

                                    specArray.add(specJson);

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

                            ObjectNode specJson = objectMapper.createObjectNode();
                            specJson.put("requirementId", req.getId());
                            specJson.put("spec", autoSpec.getSpecification());

                            specArray.add(specJson);

                            logger.info("Auto-generated code specification for requirement ID: {}", req.getId());
                        }
                    }
                }
            }

            // ==========================
            // ATTACH DB DATA TO RESPONSE
            // ==========================

            ((ObjectNode) root).set("gapReport", gapArray);
            ((ObjectNode) root).set("policyDrafts", draftArray);
            ((ObjectNode) root).set("codeSpecifications", specArray);

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

    @Transactional
    public JsonNode analyzeFromFeed(String regulationText, String sourceLink) {

        if (sourceLink != null && updateRepository.existsBySourceLink(sourceLink)) {
            logger.info("Feed item already processed: {}", sourceLink);
            return null;
        }

        logger.info("New regulatory item detected from feed: {}", sourceLink);

        String raw = analyze("", regulationText);

        JsonNode parsed = safeParse(raw);

        if (parsed != null && !parsed.has("error")) {
            persistAnalysis(parsed, regulationText, sourceLink);
        }

        return parsed;
    }

    // ================= SUB ENDPOINTS =================

    public JsonNode getRequirements(String existingPolicy, String newRegulation) {
        return analyzeAndParse(existingPolicy, newRegulation, null).path("requirements");
    }

    public JsonNode getGapReport(String existingPolicy, String newRegulation) {
        return analyzeAndParse(existingPolicy, newRegulation, null).path("gapReport");
    }

    public JsonNode getPolicyDrafts(String existingPolicy, String newRegulation) {
        return analyzeAndParse(existingPolicy, newRegulation, null).path("policyDrafts");
    }

    public JsonNode getCodeSpecifications(String existingPolicy, String newRegulation) {
        return analyzeAndParse(existingPolicy, newRegulation, null).path("codeSpecifications");
    }

    public JsonNode getSummary(String existingPolicy, String newRegulation) {
        return analyzeAndParse(existingPolicy, newRegulation, null).path("summary");
    }
}