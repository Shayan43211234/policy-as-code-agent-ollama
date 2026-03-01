package com.example.policyagent.repository;

import com.example.policyagent.entity.PolicyDraftEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PolicyDraftRepository extends JpaRepository<PolicyDraftEntity, Long> {

    List<PolicyDraftEntity> findByRequirementId(Long requirementId);
}