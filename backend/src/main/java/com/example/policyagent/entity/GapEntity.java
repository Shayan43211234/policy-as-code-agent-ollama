package com.example.policyagent.entity;

import jakarta.persistence.*;

@Entity
@Table(indexes = {
    @Index(name = "idx_gap_requirement", columnList = "requirementId")
})
public class GapEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long requirementId;

    private String issue;

    @Column(length = 5000)
    private String detail;

    // getters & setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(Long requirementId) {
        this.requirementId = requirementId;
    }

    public String getIssue() {
        return issue;
    }

    public void setIssue(String issue) {
        this.issue = issue;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
