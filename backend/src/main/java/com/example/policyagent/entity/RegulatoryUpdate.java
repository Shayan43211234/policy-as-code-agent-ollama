package com.example.policyagent.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(indexes = {
    @Index(name = "idx_reg_update_source", columnList = "sourceLink")
})
public class RegulatoryUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String authority;
    private Instant publicationDate;

    @Column(length = 20000)
    private String fullText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegulatoryStatus status; // DETECTED, ANALYZED, APPROVED, IMPLEMENTED

    private Double confidenceScore;

    private Instant createdAt = Instant.now();

    @Column(unique = true, nullable = true)
    private String sourceLink;

    // getters & setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public Instant getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(Instant publicationDate) {
        this.publicationDate = publicationDate;
    }

    public String getFullText() {
        return fullText;
    }

    public void setFullText(String fullText) {
        this.fullText = fullText;
    }

    public RegulatoryStatus getStatus() {
        return status;
    }

    public void setStatus(RegulatoryStatus status) {
        this.status = status;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getSourceLink() {
        return sourceLink;
    }

    public void setSourceLink(String sourceLink) {
        this.sourceLink = sourceLink;
    }
}