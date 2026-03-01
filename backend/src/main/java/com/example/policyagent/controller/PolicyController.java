package com.example.policyagent.controller;

import com.example.policyagent.entity.RegulatoryStatus;
import com.example.policyagent.repository.RegulatoryUpdateRepository;
import com.example.policyagent.service.PolicyService;
import com.example.policyagent.service.RegulatoryWorkflowService;
import com.example.policyagent.service.TicketService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@RestController
@RequestMapping("/api/policy")
@CrossOrigin
public class PolicyController {

    private static final Logger logger = LoggerFactory.getLogger(PolicyController.class);

    private final PolicyService service;
    private final TicketService ticketService;
    private final RegulatoryUpdateRepository regulatoryUpdateRepository;
    private final RegulatoryWorkflowService workflowService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public PolicyController(
            PolicyService service,
            TicketService ticketService,
            RegulatoryUpdateRepository regulatoryUpdateRepository,
            RegulatoryWorkflowService workflowService,
            ObjectMapper objectMapper) {
        this.service = service;
        this.ticketService = ticketService;
        this.regulatoryUpdateRepository = regulatoryUpdateRepository;
        this.workflowService = workflowService;
        this.objectMapper = objectMapper;
    }

    // ================= ANALYSIS =================

    @PostMapping("/analyze")
    public ResponseEntity<JsonNode> analyze(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(
                service.analyzeAndParse(
                        request.get("existingPolicy"),
                        request.get("newRegulation")));
    }

    @PostMapping("/requirements")
    public ResponseEntity<JsonNode> requirements(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(
                service.getRequirements(
                        request.get("existingPolicy"),
                        request.get("newRegulation")));
    }

    @PostMapping("/gap-report")
    public ResponseEntity<JsonNode> gapReport(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(
                service.getGapReport(
                        request.get("existingPolicy"),
                        request.get("newRegulation")));
    }

    @PostMapping("/policy-drafts")
    public ResponseEntity<JsonNode> policyDrafts(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(
                service.getPolicyDrafts(
                        request.get("existingPolicy"),
                        request.get("newRegulation")));
    }

    @PostMapping("/code-specs")
    public ResponseEntity<JsonNode> codeSpecs(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(
                service.getCodeSpecifications(
                        request.get("existingPolicy"),
                        request.get("newRegulation")));
    }

    @PostMapping("/summary")
    public ResponseEntity<JsonNode> summary(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(
                service.getSummary(
                        request.get("existingPolicy"),
                        request.get("newRegulation")));
    }

    // ================= RSS FEED =================

    @PostMapping("/fetch-feed")
    public ResponseEntity<?> fetchFeed(@RequestBody Map<String, String> request) {
        try {
            String url = request.get("url");

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            var doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(resp.body().getBytes()));

            var items = doc.getElementsByTagName("item");
            if (items.getLength() == 0) {
                items = doc.getElementsByTagName("entry");
            }

            List<Map<String, String>> result = new ArrayList<>();

            for (int i = 0; i < items.getLength(); i++) {
                var node = items.item(i);

                String title = "";
                String link = "";
                String desc = "";

                for (int j = 0; j < node.getChildNodes().getLength(); j++) {
                    var c = node.getChildNodes().item(j);
                    var name = c.getNodeName().toLowerCase();

                    if (name.equals("title"))
                        title = c.getTextContent();
                    if (name.equals("link"))
                        link = c.getTextContent();
                    if (name.equals("description") || name.equals("summary"))
                        desc = c.getTextContent();
                }

                Map<String, String> item = new HashMap<>();
                item.put("title", title);
                item.put("link", link);
                item.put("description", desc);

                result.add(item);
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of(
                            "error", "fetch_failed",
                            "message", e.getMessage()));
        }
    }

    // ================= TICKETS =================

    @PostMapping("/tickets")
    public ResponseEntity<?> createTicket(@RequestBody Map<String, Object> request) {

        Long requirementId = request.get("requirementId") == null
                ? null
                : Long.valueOf(request.get("requirementId").toString());

        String summary = request.getOrDefault("summary", "").toString();
        String recommendation = request.getOrDefault("recommendation", "").toString();

        return ResponseEntity.ok(
                ticketService.createTicket(requirementId, summary, recommendation));
    }

    @GetMapping("/tickets")
    public ResponseEntity<?> listTickets() {
        return ResponseEntity.ok(ticketService.listTickets());
    }

    // ================= EXPORT =================

    @PostMapping(path = "/export/pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> exportPdf(@RequestBody JsonNode payload) throws Exception {
        byte[] data = generatePdf(payload);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=policy-drafts.pdf")
                .body(data);
    }

    @PostMapping(path = "/export/docx", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> exportDocx(@RequestBody JsonNode payload) throws Exception {
        byte[] data = generateDocx(payload);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=policy-drafts.docx")
                .body(data);
    }

    // ================= WORKFLOW =================

    @PostMapping("/{id}/submit-review")
    public ResponseEntity<?> submitForReview(@PathVariable Long id) {
        return ResponseEntity.ok(
                workflowService.transition(id, RegulatoryStatus.REVIEW_PENDING, "SYSTEM"));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        return ResponseEntity.ok(
                workflowService.transition(id, RegulatoryStatus.APPROVED, "COMPLIANCE"));
    }

    @PostMapping("/{id}/mark-implemented")
    public ResponseEntity<?> markImplemented(@PathVariable Long id) {
        return ResponseEntity.ok(
                workflowService.transition(id, RegulatoryStatus.IMPLEMENTED, "TECH"));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<?> close(@PathVariable Long id) {
        return ResponseEntity.ok(
                workflowService.transition(id, RegulatoryStatus.CLOSED, "SYSTEM"));
    }

    @GetMapping("/updates")
    public ResponseEntity<?> getAllUpdates() {
        return ResponseEntity.ok(regulatoryUpdateRepository.findAll());
    }

    @GetMapping("/updates/{id}")
    public ResponseEntity<?> getUpdate(@PathVariable Long id) {
        return ResponseEntity.ok(
                regulatoryUpdateRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Not found")));
    }

    @GetMapping("/{id}/allowed-transitions")
    public ResponseEntity<?> allowedTransitions(@PathVariable Long id) {
        return ResponseEntity.ok(workflowService.getAllowedTransitions(id));
    }

    private byte[] generatePdf(JsonNode payload) throws Exception {
        String text = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream contents = new org.apache.pdfbox.pdmodel.PDPageContentStream(
                    doc, page)) {
                contents.beginText();
                contents.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 10);
                contents.setLeading(12f);
                contents.newLineAtOffset(40, 750);
                for (String line : text.split("\n")) {
                    contents.showText(line.replaceAll("[^\\x00-\\x7F]", "?"));
                    contents.newLine();
                }
                contents.endText();
            }
            try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                doc.save(baos);
                return baos.toByteArray();
            }
        }
    }

    private byte[] generateDocx(JsonNode payload) throws Exception {
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument()) {
            if (payload.has("policyDrafts") && payload.get("policyDrafts").isArray()) {
                for (JsonNode d : payload.get("policyDrafts")) {
                    org.apache.poi.xwpf.usermodel.XWPFParagraph p1 = doc.createParagraph();
                    org.apache.poi.xwpf.usermodel.XWPFRun r1 = p1.createRun();
                    r1.setBold(true);
                    r1.setText("Requirement " + d.path("requirementId").asText());
                    org.apache.poi.xwpf.usermodel.XWPFParagraph p2 = doc.createParagraph();
                    org.apache.poi.xwpf.usermodel.XWPFRun r2 = p2.createRun();
                    r2.setText(d.path("draft").asText());
                }
            } else {
                org.apache.poi.xwpf.usermodel.XWPFParagraph p = doc.createParagraph();
                org.apache.poi.xwpf.usermodel.XWPFRun r = p.createRun();
                r.setText(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
            }
            try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                doc.write(baos);
                return baos.toByteArray();
            }
        }
    }
}