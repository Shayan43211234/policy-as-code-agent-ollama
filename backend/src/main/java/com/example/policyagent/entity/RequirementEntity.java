package com.example.policyagent.entity;

import jakarta.persistence.*;

@Entity
public class RequirementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long regulatoryUpdateId;

    @Column(length = 5000)
    private String text;

    private String type;

    private Boolean satisfied;

    private String recommendation;

    // getters & setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRegulatoryUpdateId() {
        return regulatoryUpdateId;
    }

    public void setRegulatoryUpdateId(Long regulatoryUpdateId) {
        this.regulatoryUpdateId = regulatoryUpdateId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean isSatisfied() {
        return satisfied;
    }

    public void setSatisfied(Boolean satisfied) {
        this.satisfied = satisfied;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }
}
