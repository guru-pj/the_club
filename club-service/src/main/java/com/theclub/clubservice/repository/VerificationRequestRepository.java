package com.theclub.clubservice.repository;

import com.theclub.clubservice.model.VerificationRequest;
import com.theclub.clubservice.model.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, UUID> {

    boolean existsByUserIdAndStatusIn(UUID userId, Collection<VerificationStatus> statuses);

    List<VerificationRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<VerificationRequest> findByStatusOrderByCreatedAtAsc(VerificationStatus status, Pageable pageable);

    Page<VerificationRequest> findAllByOrderByCreatedAtAsc(Pageable pageable);
}
