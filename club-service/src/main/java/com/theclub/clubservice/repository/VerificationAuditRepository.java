package com.theclub.clubservice.repository;

import com.theclub.clubservice.model.VerificationAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VerificationAuditRepository extends JpaRepository<VerificationAudit, UUID> {

    List<VerificationAudit> findByRequestIdOrderByCreatedAtAsc(UUID requestId);
}
