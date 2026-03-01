package com.example.policyagent.model;

import java.time.Instant;

public class Ticket {

    private Long id;
    private Integer requirementId;
    private String summary;
    private String recommendation;
    private Instant createdAt;
    private String jiraLink;

    public Ticket() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getRequirementId() { return requirementId; }
    public void setRequirementId(Integer requirementId) { this.requirementId = requirementId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getJiraLink() { return jiraLink; }
    public void setJiraLink(String jiraLink) { this.jiraLink = jiraLink; }
}
