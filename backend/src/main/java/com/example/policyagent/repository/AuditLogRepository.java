package com.example.policyagent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.policyagent.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    
}
