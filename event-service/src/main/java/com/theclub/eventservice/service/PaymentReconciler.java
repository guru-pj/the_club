package com.theclub.eventservice.service;

import com.theclub.eventservice.model.Payment;
import com.theclub.eventservice.model.PaymentStatus;
import com.theclub.eventservice.payment.CapturedPayment;
import com.theclub.eventservice.payment.PaymentGateway;
import com.theclub.eventservice.payment.PaymentProperties;
import com.theclub.eventservice.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Safety net for payments the client never confirmed and webhooks never reported: asks the provider about stale
 * orders, settles captured ones, expires abandoned ones (freeing their spots) and retries failed refunds.
 * Each payment is handled in its own transaction so one failure does not block the batch.
 */
@Component
@ConditionalOnProperty(prefix = "theclub.payments", name = "reconcile-enabled", havingValue = "true", matchIfMissing = true)
public class PaymentReconciler {

    /** Give the client and the webhook a chance before polling the provider. */
    static final Duration GRACE = Duration.ofMinutes(2);

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciler.class);

    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final PaymentGateway gateway;
    private final PaymentProperties properties;
    private final Clock clock;

    public PaymentReconciler(PaymentRepository payments, PaymentService paymentService, PaymentGateway gateway,
                             PaymentProperties properties, Clock clock) {
        this.payments = payments;
        this.paymentService = paymentService;
        this.gateway = gateway;
        this.properties = properties;
        this.clock = clock;
    }

    public record Summary(int checked, int settled, int expired, int refundsRetried, int errors) {
    }

    @Scheduled(fixedDelayString = "${theclub.payments.reconcile-interval:PT5M}",
            initialDelayString = "${theclub.payments.reconcile-initial-delay:PT1M}")
    public void scheduledRun() {
        run();
    }

    public Summary run() {
        LocalDateTime now = LocalDateTime.now(clock);
        PageRequest batch = PageRequest.of(0, properties.reconcileBatchSize());
        int checked = 0;
        int settled = 0;
        int expired = 0;
        int refunds = 0;
        int errors = 0;

        List<Payment> stale = payments.findByStatusAndCreatedAtBeforeOrderByCreatedAt(PaymentStatus.CREATED,
                now.minus(GRACE), batch);
        for (Payment payment : stale) {
            checked++;
            try {
                Optional<CapturedPayment> captured = gateway.findCapturedPayment(payment.getProviderOrderId());
                if (captured.isPresent()) {
                    if (paymentService.settleCaptured(payment.getId(), captured.get())) {
                        settled++;
                    }
                } else if (payment.getCreatedAt().isBefore(now.minus(properties.pendingHold()))
                        && paymentService.expire(payment.getId())) {
                    expired++;
                }
            } catch (RuntimeException e) {
                errors++;
                log.warn("Reconciliation of payment {} failed: {}", payment.getId(), e.getMessage());
            }
        }

        for (Payment payment : payments.findByStatusOrderByUpdatedAt(PaymentStatus.REFUND_PENDING, batch)) {
            try {
                if (paymentService.retryRefund(payment.getId())) {
                    refunds++;
                }
            } catch (RuntimeException e) {
                errors++;
                log.warn("Refund retry of payment {} failed: {}", payment.getId(), e.getMessage());
            }
        }

        Summary summary = new Summary(checked, settled, expired, refunds, errors);
        if (checked > 0 || refunds > 0 || errors > 0) {
            log.info("Payment reconciliation: {}", summary);
        }
        return summary;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(prefix = "theclub.payments", name = "reconcile-enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfig {
    }
}
