package com.example.policyagent.service;

import com.example.policyagent.entity.*;
import com.example.policyagent.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Service
public class PolicyService {

    private static final Logger logger = LoggerFactory.getLogger(PolicyService.class);

    private final GroqChatService chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RegulatoryUpdateRepository updateRepository;
    private final RequirementRepository requirementRepository;
    private final GapRepository gapRepository;
    private final AuditLogRepository auditRepository;
    private final PolicyDraftRepository policyDraftRepository;
    private final CodeSpecificationRepository codeSpecificationRepository;

    // ===============================================================
    // BASE BANK POLICY CONTEXT
    // Used when analyzing feed items (no user-supplied existing policy).
    // Represents a typical mid-size bank's current policy baseline.
    // ===============================================================
    private static final String BASE_BANK_POLICY = """
            BANK BASELINE POLICY FRAMEWORK (Current State):

            1. VENDOR / THIRD-PARTY RISK
               - vendorRiskPolicy: enabled
               - reviewFrequencyDays: 365
               - vendorRiskTiering: basic (low / high only)
               - continuousMonitoring: disabled
               - automatedAlerts: disabled
               - loggingEnabled: false

            2. CAPITAL & LIQUIDITY
               - capitalAdequacyPolicy: Basel II compliant
               - liquidityCoverageRatio: monitored quarterly
               - stressTestingFrequency: annual
               - tokenizedSecuritiesPolicy: not addressed

            3. AML / BSA / KYC
               - amlPolicy: enabled
               - kycVerification: manual process
               - suspiciousActivityReporting: enabled
               - sanctionsScreening: daily batch
               - transactionMonitoringThreshold: $10,000

            4. CONSUMER PROTECTION
               - reputationRiskPolicy: included in supervisory framework
               - consumerComplaintsProcess: manual review
               - fairLendingPolicy: enabled

            5. ENFORCEMENT & GOVERNANCE
               - enforcementActionTracking: manual
               - boardReportingFrequency: quarterly
               - chiefComplianceOfficer: designated
               - regulatoryChangeManagement: manual process, no automation

            6. MONETARY POLICY COMPLIANCE
               - fomcGuidanceTracking: manual
               - interestRateRiskPolicy: enabled
               - discountRateMonitoring: monthly review
            """;

    // Routing assignments by recommendation type
    private static final java.util.Map<String, String> ROUTING_MAP = java.util.Map.of(
            "update_policy", "COMPLIANCE TEAM",
            "add_control", "COMPLIANCE TEAM",
            "implement_system_rule", "TECHNOLOGY TEAM",
            "no_action", "NO ROUTING REQUIRED"
    );

    public PolicyService(GroqChatService chatClient,
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

        // Use base bank policy if no existing policy is provided (e.g. from feed)
        String policyContext = (existingPolicy == null || existingPolicy.isBlank())
                ? BASE_BANK_POLICY
                : existingPolicy;

        String prompt = String.format("""
                You are the Policy-as-Code Regulatory Change Agent for a bank.

                CRITICAL INSTRUCTIONS:
                - Return ONLY raw JSON. No explanations, no markdown, no triple backticks.
                - Output MUST start with { and end with }.
                - You MUST populate ALL arrays: requirements, gapReport, policyDrafts, codeSpecifications.
                - NEVER return empty arrays for gapReport, policyDrafts, or codeSpecifications if there are unsatisfied requirements.

                THE BANK:
                Business lines: Retail Banking, Commercial Lending, Wealth Management, Treasury
                Systems: Vendor Risk System, Core Banking Platform, AML Monitoring Engine, Payments Gateway

                ANALYSIS RULES:
                - Carefully compare the Existing Policy against each requirement in the New Regulation.
                - If the existing policy already satisfies a requirement, set satisfied=true, recommendation=no_action.
                - If there is a real gap, set satisfied=false and pick the correct recommendation.
                - Do NOT create artificial gaps. Only mark unsatisfied if there is a real difference.
                - Be realistic: many requirements may already be satisfied by the existing policy.

                RECOMMENDATION VALUES (pick one per requirement):
                - no_action              → already satisfied by existing policy
                - update_policy          → policy text needs to be updated (routes to COMPLIANCE TEAM)
                - add_control            → a new control/process must be added (routes to COMPLIANCE TEAM)
                - implement_system_rule  → a system/code rule must be built (routes to TECHNOLOGY TEAM)

                FOR EACH UNSATISFIED REQUIREMENT YOU MUST:
                1. Add an entry to gapReport with a clear business explanation of what is missing.
                2. If recommendation=update_policy OR add_control: add a DETAILED policy draft to policyDrafts.
                   - Write real professional policy language with section numbers, responsible officers, timelines.
                   - Example: "Section 4.2 - Vendor Risk Assessment Frequency: All vendors classified as Critical
                     or High risk shall undergo a comprehensive risk assessment no less frequently than every 180
                     calendar days. The Head of Vendor Risk Management is responsible for ensuring timely
                     completion. Non-compliance shall trigger escalation to the Chief Risk Officer within 5 business days."
                3. If recommendation=implement_system_rule: add a DETAILED code specification to codeSpecifications.
                   - Write real executable IF/THEN rule logic with actual field names and values.
                   - Example: "IF vendor_risk_tier IN ('critical', 'high') AND (current_date - last_assessment_date) > 180
                     THEN trigger_assessment_review(vendor_id); SET vendor_status = 'REVIEW_REQUIRED';
                     NOTIFY(risk_manager, compliance_officer);"

                GAP REPORT FORMAT:
                - issue: short descriptive title of the gap
                - detail: explain what the current policy says vs what the regulation requires, and the business impact.

                CONFIDENCE SCORE RULES (calculate a value between 0.50 and 1.0, do NOT hardcode):
                - Start at 1.0 and deduct based on the following:
                - Deduct 0.05 for each requirement where the rationale is uncertain or ambiguous
                - Deduct 0.10 if the existing policy text is vague, incomplete, or missing key details
                - Deduct 0.10 if the regulation text itself is ambiguous or lacks specific thresholds
                - Deduct 0.05 for each requirement marked satisfied where the evidence is weak or inferred
                - Deduct 0.05 if the regulation only partially overlaps with the bank's business lines
                - Minimum score is 0.50 regardless of deductions
                - A clear, specific regulation vs a detailed existing policy should score 0.85-0.95
                - A vague regulation vs an incomplete policy should score 0.50-0.65
                - A moderate regulation with some gaps should score 0.65-0.80
                - ALWAYS calculate and return a unique score — never default to 0.85

                Return JSON in EXACTLY this structure:

                {
                  "agentName": "Policy-as-Code Regulatory Change Agent",
                  "changeDetected": true,
                  "confidenceScore": 0.0,
                  "requirements": [
                    {
                      "id": 1,
                      "text": "exact requirement text",
                      "type": "policy|control|system",
                      "tests": ["field_name operator value"],
                      "satisfied": false,
                      "rationale": "specific explanation comparing existing policy to this requirement",
                      "recommendation": "no_action|update_policy|add_control|implement_system_rule",
                      "routingTeam": "COMPLIANCE TEAM|TECHNOLOGY TEAM|NO ROUTING REQUIRED",
                      "impactedBusinessLine": "one of the four business lines",
                      "impactedSystem": "one of the four systems"
                    }
                  ],
                  "summary": {
                    "totalRequirements": 0,
                    "alreadySatisfied": 0,
                    "policyUpdatesNeeded": 0,
                    "newControlsNeeded": 0,
                    "systemImplementationsNeeded": 0
                  },
                  "gapReport": [
                    {
                      "requirementId": 1,
                      "issue": "short issue title",
                      "detail": "current policy says X, regulation requires Y — business impact explanation"
                    }
                  ],
                  "policyDrafts": [
                    {
                      "requirementId": 1,
                      "draft": "full professional policy language with section numbers and responsible officers"
                    }
                  ],
                  "codeSpecifications": [
                    {
                      "requirementId": 1,
                      "spec": "IF condition THEN action; full executable rule logic with real field names"
                    }
                  ]
                }

                Existing Policy:
                %s

                New Regulation:
                %s
                """, policyContext, newRegulation);
        

        String raw = chatClient.chat(prompt);

        logger.debug("Raw LLM response (first 500 chars): {}",
                raw.length() > 500 ? raw.substring(0, 500) + "..." : raw);
        logger.info("=== analyze() END === response length: {}", raw.length());
        return raw;
    }

    private JsonNode safeParse(String json) {
        logger.info("=== safeParse() START ===");

        try {
            String cleanJson = json;

            if (cleanJson.contains("```json")) {
                int start = cleanJson.indexOf("```json") + 7;
                int end = cleanJson.indexOf("```", start);
                if (end > start) cleanJson = cleanJson.substring(start, end).trim();
            } else if (cleanJson.contains("```")) {
                int start = cleanJson.indexOf("```") + 3;
                int end = cleanJson.indexOf("```", start);
                if (end > start) cleanJson = cleanJson.substring(start, end).trim();
            }

            int jsonStart = cleanJson.indexOf('{');
            int jsonEnd = cleanJson.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                cleanJson = cleanJson.substring(jsonStart, jsonEnd + 1);
            }

            JsonNode parsed = objectMapper.readTree(cleanJson);
            logger.info("JSON parsing succeeded - root has {} fields", parsed.size());
            logger.info("=== safeParse() END - SUCCESS ===");
            return parsed;

        } catch (Exception e) {
            logger.error("JSON parsing FAILED: {}", e.getMessage());
            ObjectNode err = objectMapper.createObjectNode();
            err.put("error", "failed_to_parse_response");
            err.put("message", e.getMessage());
            err.put("originalLength", json.length());
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

            // 1. SAVE REGULATORY UPDATE
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

            // 2. SAVE REQUIREMENTS
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
                                    ? impactedBusinessLine : "Unknown");
                    req.setImpactedSystem(
                            impactedSystem != null && !impactedSystem.isBlank()
                                    ? impactedSystem : "Unknown");

                    req = requirementRepository.save(req);
                    ((ObjectNode) r).put("dbId", req.getId());

                    // Add routing team to the response node
                    String routingTeam = r.path("routingTeam").asText(
                            ROUTING_MAP.getOrDefault(recommendation, "COMPLIANCE TEAM"));
                    ((ObjectNode) r).put("routingTeam", routingTeam);

                    reqCount++;
                    logger.info("Requirement saved ID: {} | routing: {}", req.getId(), routingTeam);

                    boolean gapCreatedFromLLM = false;

                    // 3. GAP HANDLING — prefer LLM, fallback if missing
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

                    // Fallback gap only if LLM didn't provide one
                    if (!req.getSatisfied() && !gapCreatedFromLLM) {
                        GapEntity autoGap = new GapEntity();
                        autoGap.setRequirementId(req.getId());
                        autoGap.setIssue("Compliance gap detected");
                        autoGap.setDetail("This requirement is not satisfied by the current policy. " +
                                "Action required: " + req.getRecommendation().replace("_", " ") +
                                ". Route to: " + ROUTING_MAP.getOrDefault(req.getRecommendation(), "COMPLIANCE TEAM"));
                        gapRepository.save(autoGap);

                        ObjectNode gapJson = objectMapper.createObjectNode();
                        gapJson.put("requirementId", req.getId());
                        gapJson.put("issue", autoGap.getIssue());
                        gapJson.put("detail", autoGap.getDetail());
                        gapArray.add(gapJson);

                        logger.info("Fallback gap created for requirement ID: {}", req.getId());
                    }

                    // 4. POLICY DRAFT — for update_policy and add_control
                    if ("update_policy".equalsIgnoreCase(req.getRecommendation())
                            || "add_control".equalsIgnoreCase(req.getRecommendation())) {

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
                                    draftJson.put("routingTeam", "COMPLIANCE TEAM");
                                    draftArray.add(draftJson);

                                    logger.info("Policy draft saved from LLM for requirement ID: {}", req.getId());
                                }
                            }
                        }

                        if (!draftCreated) {
                            PolicyDraftEntity autoDraft = new PolicyDraftEntity();
                            autoDraft.setRequirementId(req.getId());
                            autoDraft.setDraft("Policy update required for: " + req.getText() +
                                    "\n\nPlease have your Compliance Team draft the updated policy language. " +
                                    "Routing: COMPLIANCE TEAM.");
                            policyDraftRepository.save(autoDraft);

                            ObjectNode draftJson = objectMapper.createObjectNode();
                            draftJson.put("requirementId", req.getId());
                            draftJson.put("draft", autoDraft.getDraft());
                            draftJson.put("routingTeam", "COMPLIANCE TEAM");
                            draftArray.add(draftJson);

                            logger.info("Fallback policy draft for requirement ID: {}", req.getId());
                        }
                    }

                    // 5. CODE SPEC — for implement_system_rule
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
                                    specJson.put("routingTeam", "TECHNOLOGY TEAM");
                                    specArray.add(specJson);

                                    logger.info("Code spec saved from LLM for requirement ID: {}", req.getId());
                                }
                            }
                        }

                        if (!specCreated) {
                            CodeSpecificationEntity autoSpec = new CodeSpecificationEntity();
                            autoSpec.setRequirementId(req.getId());
                            autoSpec.setSpecification(
                                    "// System rule required for: " + req.getText() + "\n" +
                                    "// Route to: TECHNOLOGY TEAM\n" +
                                    "// Please implement the appropriate system logic.");
                            codeSpecificationRepository.save(autoSpec);

                            ObjectNode specJson = objectMapper.createObjectNode();
                            specJson.put("requirementId", req.getId());
                            specJson.put("spec", autoSpec.getSpecification());
                            specJson.put("routingTeam", "TECHNOLOGY TEAM");
                            specArray.add(specJson);

                            logger.info("Fallback code spec for requirement ID: {}", req.getId());
                        }
                    }
                }
            }

            // ATTACH DB DATA TO RESPONSE
            ((ObjectNode) root).set("gapReport", gapArray);
            ((ObjectNode) root).set("policyDrafts", draftArray);
            ((ObjectNode) root).set("codeSpecifications", specArray);

            // 6. AUDIT LOG
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

        // Feed items always use base bank policy as the existing policy context
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