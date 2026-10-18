package com.theclub.notificationservice.repository;

import com.theclub.notificationservice.model.EmailOutbox;
import com.theclub.notificationservice.model.EmailStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    Page<EmailOutbox> findByStatusOrderByCreatedAtDesc(EmailStatus status, Pageable pageable);

    Page<EmailOutbox> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(EmailStatus status);

    /** Puts rows whose dispatcher died mid-send back in the queue (at-least-once delivery). */
    @Modifying
    @Query("update EmailOutbox e set e.status = com.theclub.notificationservice.model.EmailStatus.PENDING, "
            + "e.claimedAt = null where e.status = com.theclub.notificationservice.model.EmailStatus.SENDING "
            + "and e.claimedAt < :before")
    int releaseStuck(LocalDateTime before);
}
