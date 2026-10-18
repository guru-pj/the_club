package com.theclub.eventservice.service;

import com.theclub.eventservice.client.ClubClient;
import com.theclub.eventservice.client.NotificationClient;
import com.theclub.eventservice.client.UserClient;
import com.theclub.eventservice.repository.PaymentRepository;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * With several event-service replicas, only the instance holding the "payment-reconciler" lock runs the batch.
 */
@SpringBootTest(properties = {
        "theclub.payments.reconcile-enabled=true",
        // never fire on its own during the test; the test calls the scheduled method directly
        "theclub.payments.reconcile-initial-delay=PT24H"})
@ActiveProfiles("test")
class PaymentReconcilerLockIntegrationTest {

    @Autowired
    PaymentReconciler reconciler;
    @Autowired
    LockProvider lockProvider;
    @MockitoSpyBean
    PaymentRepository payments;
    @MockitoBean
    ClubClient clubClient;
    @MockitoBean
    UserClient userClient;
    @MockitoBean
    NotificationClient notificationClient;

    @Test
    void skipsTheRunWhileAnotherReplicaHoldsTheLock() {
        Optional<SimpleLock> otherReplica = lockProvider.lock(new LockConfiguration(Instant.now(),
                PaymentReconciler.LOCK_NAME, Duration.ofMinutes(5), Duration.ZERO));
        assertThat(otherReplica).isPresent();

        reconciler.scheduledRun();
        verify(payments, never()).findByStatusAndCreatedAtBeforeOrderByCreatedAt(any(), any(), any());

        otherReplica.get().unlock();
        clearInvocations(payments);

        reconciler.scheduledRun();
        verify(payments).findByStatusAndCreatedAtBeforeOrderByCreatedAt(any(), any(), any());
    }
}
