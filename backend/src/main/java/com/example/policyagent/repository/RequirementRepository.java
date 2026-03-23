package com.example.policyagent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.policyagent.entity.RequirementEntity;

import java.util.List;

public interface RequirementRepository extends JpaRepository<RequirementEntity, Long> {

    List<RequirementEntity> findByRegulatoryUpdateId(Long regulatoryUpdateId);
}