package com.example.policyagent.entity;

public enum RegulatoryStatus {

    NEW,
    ANALYZED,
    REVIEW_PENDING,
    APPROVED,
    IMPLEMENTED,
    CLOSED;

    public boolean canTransitionTo(RegulatoryStatus target) {

        if (this == target) return false;

        return switch (this) {
            case NEW -> target == ANALYZED;
            case ANALYZED -> target == REVIEW_PENDING;
            case REVIEW_PENDING -> target == APPROVED;
            case APPROVED -> target == IMPLEMENTED;
            case IMPLEMENTED -> target == CLOSED;
            default -> false;
        };
    }
}