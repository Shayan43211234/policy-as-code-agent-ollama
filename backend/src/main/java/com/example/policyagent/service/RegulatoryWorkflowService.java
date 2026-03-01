package com.example.policyagent.service;

import com.example.policyagent.entity.*;
import com.example.policyagent.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.Instant;

@Service
public class RegulatoryWorkflowService {

    private final RegulatoryUpdateRepository updateRepository;
    private final AuditLogRepository auditRepository;

    public RegulatoryWorkflowService(RegulatoryUpdateRepository updateRepository,
            AuditLogRepository auditRepository) {
        this.updateRepository = updateRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public RegulatoryUpdate transition(Long id, RegulatoryStatus targetStatus, String actor) {

        RegulatoryUpdate update = updateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Regulatory update not found"));

        RegulatoryStatus current = update.getStatus();

        if (!current.canTransitionTo(targetStatus)) {
            throw new IllegalStateException(
                    "Invalid transition from " + current + " to " + targetStatus);
        }

        update.setStatus(targetStatus);
        update = updateRepository.save(update);

        AuditLog log = new AuditLog();
        log.setAction("STATUS_TRANSITION");
        log.setActor(actor);
        log.setDetails("Transitioned from " + current + " to " + targetStatus);
        log.setTimestamp(Instant.now());
        auditRepository.save(log);

        return update;
    }

    public List<RegulatoryStatus> getAllowedTransitions(Long id) {
        RegulatoryUpdate update = updateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Not found"));

        RegulatoryStatus current = update.getStatus();

        return Arrays.stream(RegulatoryStatus.values())
                .filter(current::canTransitionTo)
                .toList();
    }
}