package com.example.policyagent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.policyagent.entity.RequirementEntity;

public interface RequirementRepository extends JpaRepository<RequirementEntity, Long> {
    
}
