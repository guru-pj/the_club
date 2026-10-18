package com.theclub.eventservice.service;

import com.theclub.eventservice.model.Payment;
import com.theclub.eventservice.model.PaymentProvider;
import com.theclub.eventservice.model.PaymentStatus;
import com.theclub.eventservice.model.Registration;
import com.theclub.eventservice.payment.CapturedPayment;
import com.theclub.eventservice.payment.PaymentGateway;
import com.theclub.eventservice.payment.PaymentProperties;
import com.theclub.eventservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.theclub.eventservice.service.TestData.CLOCK;
import static com.theclub.eventservice.service.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentReconcilerTest {

    @Mock
    PaymentRepository payments;
    @Mock
    PaymentService paymentService;
    @Mock
    PaymentGateway gateway;

    PaymentReconciler reconciler;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties("mock", null, null, null, true, null, 10);
        reconciler = new PaymentReconciler(payments, paymentService, gateway, properties, CLOCK);
    }

    private Payment createdMinutesAgo(long minutes) {
        Payment payment = new Payment(new Registration(UUID.randomUUID(), UUID.randomUUID()), new BigDecimal("100"),
                "INR", PaymentProvider.RAZORPAY, "order_" + minutes);
        payment.setCreatedAt(NOW.minusMinutes(minutes));
        return payment;
    }

    @Test
    void settlesCapturedExpiresAbandonedAndLeavesRecentOnesAlone() {
        Payment captured = createdMinutesAgo(5);
        Payment abandoned = createdMinutesAgo(30);
        Payment stillInHold = createdMinutesAgo(10);
        when(payments.findByStatusAndCreatedAtBeforeOrderByCreatedAt(eq(PaymentStatus.CREATED),
                eq(NOW.minus(PaymentReconciler.GRACE)), any())).thenReturn(List.of(captured, abandoned, stillInHold));
        CapturedPayment capture = new CapturedPayment("pay_1", 10_000, "INR");
        when(gateway.findCapturedPayment("order_5")).thenReturn(Optional.of(capture));
        when(gateway.findCapturedPayment("order_30")).thenReturn(Optional.empty());
        when(gateway.findCapturedPayment("order_10")).thenReturn(Optional.empty());
        when(paymentService.settleCaptured(captured.getId(), capture)).thenReturn(true);
        when(paymentService.expire(abandoned.getId())).thenReturn(true);

        PaymentReconciler.Summary summary = reconciler.run();

        assertThat(summary).isEqualTo(new PaymentReconciler.Summary(3, 1, 1, 0, 0));
        verify(paymentService, never()).expire(stillInHold.getId());
        verify(paymentService, never()).expire(captured.getId());
    }

    @Test
    void retriesPendingRefundsAndKeepsGoingAfterErrors() {
        Payment broken = createdMinutesAgo(40);
        Payment fine = createdMinutesAgo(50);
        when(payments.findByStatusAndCreatedAtBeforeOrderByCreatedAt(any(), any(), any())).thenReturn(List.of(broken, fine));
        when(gateway.findCapturedPayment("order_40")).thenThrow(new IllegalStateException("timeout"));
        when(gateway.findCapturedPayment("order_50")).thenReturn(Optional.empty());
        when(paymentService.expire(fine.getId())).thenReturn(true);
        Payment refund = createdMinutesAgo(60);
        when(payments.findByStatusOrderByUpdatedAt(eq(PaymentStatus.REFUND_PENDING), any())).thenReturn(List.of(refund));
        when(paymentService.retryRefund(refund.getId())).thenReturn(true);

        PaymentReconciler.Summary summary = reconciler.run();

        assertThat(summary).isEqualTo(new PaymentReconciler.Summary(2, 0, 1, 1, 1));
    }
}
