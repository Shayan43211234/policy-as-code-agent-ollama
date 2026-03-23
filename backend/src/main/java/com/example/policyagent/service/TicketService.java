package com.example.policyagent.service;

import com.example.policyagent.entity.*;
import com.example.policyagent.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final RequirementRepository requirementRepository;
    private final RegulatoryUpdateRepository updateRepository;
    private final AuditLogRepository auditRepository;

    public TicketService(TicketRepository ticketRepository,
                         RequirementRepository requirementRepository,
                         RegulatoryUpdateRepository updateRepository,
                         AuditLogRepository auditRepository) {
        this.ticketRepository = ticketRepository;
        this.requirementRepository = requirementRepository;
        this.updateRepository = updateRepository;
        this.auditRepository = auditRepository;
    }

    /**
     * Determines routing team based on recommendation type:
     * - update_policy  → COMPLIANCE (legal/policy team reviews policy language)
     * - add_control    → COMPLIANCE (compliance team owns control design)
     * - implement_system_rule → TECHNOLOGY (tech team builds system rules)
     * - no_action      → RISK (risk team confirms no action needed)
     */
    private String resolveAssignedTeam(String recommendation) {
        if (recommendation == null) return "COMPLIANCE";
        return switch (recommendation.toLowerCase()) {
            case "implement_system_rule" -> "TECHNOLOGY";
            case "no_action"             -> "RISK";
            default                      -> "COMPLIANCE"; // update_policy, add_control
        };
    }

    @Transactional
    public TicketEntity createTicket(Long requirementId,
                                     String summary,
                                     String recommendation) {

        RequirementEntity req = requirementRepository.findById(requirementId)
                .orElseThrow(() -> new RuntimeException("Requirement not found: " + requirementId));

        RegulatoryUpdate update = updateRepository.findById(req.getRegulatoryUpdateId())
                .orElseThrow(() -> new RuntimeException("Regulatory update not found"));

        // Step 1: Create ticket
        TicketEntity ticket = new TicketEntity();
        ticket.setRequirementId(requirementId);
        ticket.setRegulatoryUpdateId(update.getId());
        ticket.setSummary(summary);
        ticket.setRecommendation(recommendation);
        ticket.setAssignedTeam(resolveAssignedTeam(recommendation));
        ticket.setStatus(TicketStatus.OPEN);

        ticket = ticketRepository.save(ticket);

        // Step 2: Generate tracking key
        String trackingKey = "REG-" + update.getId()
                + "-REQ-" + requirementId
                + "-TCK-" + ticket.getId();

        ticket.setTrackingKey(trackingKey);
        ticket = ticketRepository.save(ticket);

        // Step 3: Audit log
        AuditLog log = new AuditLog();
        log.setAction("TICKET_CREATED");
        log.setActor("SYSTEM");
        log.setDetails("Ticket " + trackingKey
                + " created for requirement #" + requirementId
                + " — routed to " + ticket.getAssignedTeam());
        log.setTimestamp(Instant.now());
        auditRepository.save(log);

        return ticket;
    }

    @Transactional
    public TicketEntity transitionStatus(Long ticketId,
                                         TicketStatus target,
                                         String actor) {

        TicketEntity ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        TicketStatus current = ticket.getStatus();

        if (!current.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Invalid transition from " + current + " to " + target);
        }

        ticket.setStatus(target);
        ticket = ticketRepository.save(ticket);

        AuditLog log = new AuditLog();
        log.setAction("TICKET_STATUS_TRANSITION");
        log.setActor(actor);
        log.setDetails("Ticket " + ticket.getTrackingKey()
                + " transitioned from " + current + " to " + target);
        log.setTimestamp(Instant.now());
        auditRepository.save(log);

        return ticket;
    }

    public List<TicketEntity> listTickets() {
        return ticketRepository.findAll();
    }
}