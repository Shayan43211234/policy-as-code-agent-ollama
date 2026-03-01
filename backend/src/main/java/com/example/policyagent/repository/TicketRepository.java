package com.example.policyagent.repository;

import com.example.policyagent.entity.TicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketRepository extends JpaRepository<TicketEntity, Long> {

    List<TicketEntity> findByRegulatoryUpdateId(Long regulatoryUpdateId);

    List<TicketEntity> findByRequirementId(Long requirementId);
}