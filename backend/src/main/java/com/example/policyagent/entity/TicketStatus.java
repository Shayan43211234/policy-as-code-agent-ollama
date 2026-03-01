package com.example.policyagent.entity;

public enum TicketStatus {

    OPEN,
    IN_PROGRESS,
    COMPLETED;

    public boolean canTransitionTo(TicketStatus target) {
        return switch (this) {
            case OPEN -> target == IN_PROGRESS;
            case IN_PROGRESS -> target == COMPLETED;
            default -> false;
        };
    }
}