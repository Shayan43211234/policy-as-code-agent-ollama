package com.example.policyagent.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ticket_entity",
       indexes = {
           @Index(name = "idx_ticket_requirement", columnList = "requirement_id"),
           @Index(name = "idx_ticket_update", columnList = "regulatory_update_id"),
           @Index(name = "idx_ticket_tracking", columnList = "tracking_key")
       })
public class TicketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_key", unique = true)
    private String trackingKey;

    @Column(name = "regulatory_update_id", nullable = false)
    private Long regulatoryUpdateId;

    @Column(name = "requirement_id", nullable = false)
    private Long requirementId;

    @Column(nullable = false, length = 5000)
    private String summary;

    @Column(nullable = false)
    private String recommendation;

    // COMPLIANCE | TECHNOLOGY | RISK
    @Column(name = "assigned_team", nullable = false)
    private String assignedTeam;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.OPEN;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // ===== Getters & Setters =====

    public Long getId() { return id; }

    public String getTrackingKey() { return trackingKey; }
    public void setTrackingKey(String trackingKey) { this.trackingKey = trackingKey; }

    public Long getRegulatoryUpdateId() { return regulatoryUpdateId; }
    public void setRegulatoryUpdateId(Long regulatoryUpdateId) { this.regulatoryUpdateId = regulatoryUpdateId; }

    public Long getRequirementId() { return requirementId; }
    public void setRequirementId(Long requirementId) { this.requirementId = requirementId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

    public String getAssignedTeam() { return assignedTeam; }
    public void setAssignedTeam(String assignedTeam) { this.assignedTeam = assignedTeam; }

    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}