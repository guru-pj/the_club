package com.theclub.eventservice.repository;

import com.theclub.eventservice.model.Payment;
import com.theclub.eventservice.model.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** Serializes settlement of one payment across confirm, webhook and reconciliation. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.providerOrderId = :orderId")
    Optional<Payment> findByProviderOrderIdForUpdate(@Param("orderId") String providerOrderId);

    List<Payment> findByStatusAndCreatedAtBeforeOrderByCreatedAt(PaymentStatus status, LocalDateTime before,
                                                                Pageable pageable);

    List<Payment> findByStatusOrderByUpdatedAt(PaymentStatus status, Pageable pageable);

    List<Payment> findByEventIdAndStatus(UUID eventId, PaymentStatus status);

    List<Payment> findByEventId(UUID eventId);

    List<Payment> findByRegistrationId(UUID registrationId);

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);
}
