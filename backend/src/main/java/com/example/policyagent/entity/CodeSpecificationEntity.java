package com.example.policyagent.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "code_specification_entity",
       indexes = {
           @Index(name = "idx_code_spec_requirement", columnList = "requirement_id")
       })
public class CodeSpecificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requirement_id", nullable = false)
    private Long requirementId;

    @Column(name = "specification", columnDefinition = "TEXT", nullable = false)
    private String specification;

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

    public String getSpecification() {
        return specification;
    }

    public void setSpecification(String specification) {
        this.specification = specification;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}