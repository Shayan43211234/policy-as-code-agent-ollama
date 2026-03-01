package com.example.policyagent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.policyagent.entity.GapEntity;

public interface GapRepository extends JpaRepository<GapEntity, Long> {
    
}
