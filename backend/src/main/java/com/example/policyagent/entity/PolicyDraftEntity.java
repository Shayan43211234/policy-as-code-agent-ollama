package com.example.policyagent.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "policy_draft_entity",
       indexes = {
           @Index(name = "idx_policy_draft_requirement", columnList = "requirement_id")
       })
public class PolicyDraftEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requirement_id", nullable = false)
    private Long requirementId;

    @Column(name = "draft", columnDefinition = "TEXT", nullable = false)
    private String draft;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // =====================
    // Getters and Setters
    // =====================

    public Long getId() {
        return id;
    }

    public Long getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(Long requirementId) {
        this.requirementId = requirementId;
    }

    public String getDraft() {
        return draft;
    }

    public void setDraft(String draft) {
        this.draft = draft;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}