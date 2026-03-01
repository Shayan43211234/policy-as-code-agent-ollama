package com.example.policyagent.repository;

import com.example.policyagent.entity.CodeSpecificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodeSpecificationRepository extends JpaRepository<CodeSpecificationEntity, Long> {

    List<CodeSpecificationEntity> findByRequirementId(Long requirementId);
}