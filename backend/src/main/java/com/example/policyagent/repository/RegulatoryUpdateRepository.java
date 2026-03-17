package com.example.policyagent.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.policyagent.entity.RegulatoryUpdate;

public interface RegulatoryUpdateRepository extends JpaRepository<RegulatoryUpdate, Long> {

    boolean existsBySourceLink(String sourceLink);

    Optional<RegulatoryUpdate> findBySourceLink(String sourceLink);

    Optional<RegulatoryUpdate> findTopByOrderByIdDesc();
}
