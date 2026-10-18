package com.theclub.eventservice.service;

import com.theclub.common.observability.BusinessMetrics;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import com.theclub.eventservice.dto.ConfirmPaymentRequest;
import com.theclub.eventservice.dto.MockSignatureResponse;
import com.theclub.eventservice.dto.PaymentResponse;
import com.theclub.eventservice.model.Event;
import com.theclub.eventservice.model.EventStatus;
import com.theclub.eventservice.model.Payment;
import com.theclub.eventservice.model.PaymentStatus;
import com.theclub.eventservice.model.Registration;
import com.theclub.eventservice.model.RegistrationStatus;
import com.theclub.eventservice.payment.CapturedPayment;
import com.theclub.eventservice.payment.MockPaymentGateway;
import com.theclub.eventservice.payment.Money;
import com.theclub.eventservice.payment.PaymentGateway;
import com.theclub.eventservice.payment.PaymentProperties;
import com.theclub.eventservice.repository.EventRepository;
import com.theclub.eventservice.repository.PaymentRepository;
import com.theclub.eventservice.repository.RegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Payment lifecycle. Every way a payment can be settled — the client's confirm call, the provider webhook and the
 * reconciliation job — goes through {@link #markPaid}, which is idempotent and runs under a row lock.
 */
@Service
public class PaymentService {

    static final String REASON_EVENT_CANCELLED = "The event was cancelled";
    static final String REASON_EVENT_FULL = "The event filled up before your payment was confirmed";
    static final String REASON_DUPLICATE = "Duplicate payment for an existing registration";
    static final String REASON_BY_CLUB = "Refunded by the club";

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final RegistrationRepository registrations;
    private final EventRepository events;
    private final PaymentGateway gateway;
    private final TicketIssuer ticketIssuer;
    private final ConfirmationNotifier notifier;
    private final MemberEmails memberEmails;
    private final ClubAccess clubAccess;
    private final PaymentProperties properties;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, RegistrationRepository registrations, EventRepository events,
                          PaymentGateway gateway, TicketIssuer ticketIssuer, ConfirmationNotifier notifier,
                          MemberEmails memberEmails, ClubAccess clubAccess, PaymentProperties properties,
                          Clock clock) {
        this.payments = payments;
        this.registrations = registrations;
        this.events = events;
        this.gateway = gateway;
        this.ticketIssuer = ticketIssuer;
        this.notifier = notifier;
        this.memberEmails = memberEmails;
        this.clubAccess = clubAccess;
        this.properties = properties;
        this.clock = clock;
    }

    /** How long an unpaid registration holds its spot. */
    public Duration pendingHold() {
        return properties.pendingHold();
    }

    public String providerKeyId() {
        return gateway.keyId();
    }

    /** The amount always comes from the event, never from the client. */
    @Transactional
    public Payment createFor(Registration registration, Event event) {
        String orderId = gateway.createOrder(event.getPrice(), event.getCurrency(), registration.getId().toString());
        Payment payment = new Payment(registration, event.getPrice(), event.getCurrency(), gateway.provider(), orderId);
        payment.setCreatedAt(LocalDateTime.now(clock));
        log.info("Payment {} created for registration {} ({} {})", payment.getId(), registration.getId(),
                payment.getAmount(), payment.getCurrency());
        BusinessMetrics.increment("payments.created", "provider", String.valueOf(gateway.provider()).toLowerCase(java.util.Locale.ROOT));
        return payments.save(payment);
    }

    /**
     * Client-side confirmation with the checkout signature. A bad signature marks a fresh payment FAILED (kept, no
     * rollback) and answers 400; a valid one settles the payment even if it had failed or expired, since the
     * signature proves the provider took the money.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public PaymentResponse confirm(UUID paymentId, ConfirmPaymentRequest request, AuthenticatedUser caller) {
        Payment payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment not found"));
        if (!payment.getUserId().equals(caller.id())) {
            throw ApiException.forbidden("This payment belongs to another member");
        }
        if (!payment.getStatus().isSettled()) {
            if (!gateway.verify(payment.getProviderOrderId(), request.providerPaymentId(), request.signature())) {
                if (payment.getStatus() == PaymentStatus.CREATED) {
                    transition(payment, PaymentStatus.FAILED);
                }
                throw ApiException.badRequest("Payment verification failed");
            }
            markPaid(payment, request.providerPaymentId(), caller::email);
        }
        return PaymentResponse.of(payment, registrationStatus(payment));
    }

    /**
     * Settles the payment for a provider order reported as captured by a webhook.
     *
     * @return false when the order is unknown or the amount/currency does not match (logged, not settled).
     */
    @Transactional
    public boolean settleCapturedOrder(String providerOrderId, String providerPaymentId, long amountMinor,
                                       String currency) {
        Payment payment = payments.findByProviderOrderIdForUpdate(providerOrderId).orElse(null);
        if (payment == null) {
            log.warn("Capture reported for unknown order {}", providerOrderId);
            return false;
        }
        return settle(payment, new CapturedPayment(providerPaymentId, amountMinor, currency));
    }

    /** Settles a payment the reconciliation job found captured at the provider. */
    @Transactional
    public boolean settleCaptured(UUID paymentId, CapturedPayment captured) {
        Payment payment = payments.findByIdForUpdate(paymentId).orElse(null);
        return payment != null && settle(payment, captured);
    }

    /** A webhook reported a failed attempt; the member may still retry against the same order. */
    @Transactional
    public void markFailedByOrder(String providerOrderId) {
        payments.findByProviderOrderIdForUpdate(providerOrderId)
                .filter(p -> p.getStatus() == PaymentStatus.CREATED)
                .ifPresent(p -> transition(p, PaymentStatus.FAILED));
    }

    /** The provider confirmed a refund we could not complete synchronously. */
    @Transactional
    public void markRefundProcessed(String providerPaymentId, String refundId) {
        payments.findByProviderPaymentId(providerPaymentId)
                .filter(p -> p.getStatus() == PaymentStatus.REFUND_PENDING)
                .ifPresent(p -> {
                    p.setRefundId(refundId);
                    p.setRefundedAt(LocalDateTime.now(clock));
                    transition(p, PaymentStatus.REFUNDED);
                });
    }

    /**
     * Releases a payment that was not completed within the hold: the payment becomes EXPIRED and, unless the member
     * has started a newer payment, the registration is cancelled so the spot is freed.
     */
    @Transactional
    public boolean expire(UUID paymentId) {
        Payment payment = payments.findByIdForUpdate(paymentId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.CREATED) {
            return false;
        }
        transition(payment, PaymentStatus.EXPIRED);
        boolean newerAttempt = payments.findByRegistrationId(payment.getRegistrationId()).stream()
                .anyMatch(p -> !p.getId().equals(payment.getId()) && p.getStatus() == PaymentStatus.CREATED);
        Registration registration = registrations.findById(payment.getRegistrationId()).orElse(null);
        if (!newerAttempt && registration != null && registration.getStatus() == RegistrationStatus.PENDING_PAYMENT) {
            registration.setStatus(RegistrationStatus.CANCELLED);
            log.info("Registration {} released: payment {} expired", registration.getId(), payment.getId());
        }
        return true;
    }

    /** Retries a refund the provider did not accept earlier. */
    @Transactional
    public boolean retryRefund(UUID paymentId) {
        Payment payment = payments.findByIdForUpdate(paymentId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.REFUND_PENDING) {
            return false;
        }
        return callRefund(payment);
    }

    /** Owner/admin refund of a successful payment; the registration is cancelled. */
    @Transactional
    public PaymentResponse refund(UUID paymentId, String reason, AuthenticatedUser caller) {
        Payment payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment not found"));
        Event event = events.findById(payment.getEventId())
                .orElseThrow(() -> ApiException.notFound("Event not found"));
        clubAccess.requireOwnerOrAdmin(event.getClubId(), caller);
        if (payment.getStatus() == PaymentStatus.REFUNDED || payment.getStatus() == PaymentStatus.REFUND_PENDING) {
            throw ApiException.conflict("This payment has already been refunded");
        }
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw ApiException.conflict("Only successful payments can be refunded");
        }
        Registration registration = registrations.findById(payment.getRegistrationId())
                .orElseThrow(() -> ApiException.notFound("Registration not found"));
        if (registration.getStatus() == RegistrationStatus.CHECKED_IN) {
            throw ApiException.conflict("Cannot refund a checked-in registration");
        }
        cancelRegistration(registration);
        issueRefund(payment, event, reason == null || reason.isBlank() ? REASON_BY_CLUB : reason.trim());
        return PaymentResponse.of(payment, registration.getStatus());
    }

    /**
     * Refunds every successful payment of a cancelled event. Best effort per payment: provider failures leave the
     * payment REFUND_PENDING for the reconciliation job. Must run inside the cancelling transaction.
     */
    @Transactional
    public int refundAllForCancelledEvent(Event event) {
        int refunded = 0;
        for (Payment candidate : payments.findByEventIdAndStatus(event.getId(), PaymentStatus.SUCCEEDED)) {
            Payment payment = payments.findByIdForUpdate(candidate.getId()).orElse(null);
            if (payment == null || payment.getStatus() != PaymentStatus.SUCCEEDED) {
                continue;
            }
            Registration registration = registrations.findById(payment.getRegistrationId()).orElse(null);
            if (registration != null && registration.getStatus() == RegistrationStatus.CHECKED_IN) {
                log.warn("Not refunding payment {}: member already checked in", payment.getId());
                continue;
            }
            if (registration != null) {
                cancelRegistration(registration);
            }
            issueRefund(payment, event, REASON_EVENT_CANCELLED);
            refunded++;
        }
        return refunded;
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID paymentId, AuthenticatedUser caller) {
        Payment payment = payments.findById(paymentId).orElseThrow(() -> ApiException.notFound("Payment not found"));
        if (!caller.isAdmin() && !payment.getUserId().equals(caller.id())) {
            throw ApiException.forbidden("This payment belongs to another member");
        }
        return PaymentResponse.of(payment, registrationStatus(payment));
    }

    /**
     * Development helper: signs a fake provider payment id the way checkout would. Only exists with the mock provider.
     */
    @Transactional(readOnly = true)
    public MockSignatureResponse mockSignature(UUID paymentId, String providerPaymentId, AuthenticatedUser caller) {
        if (!(gateway instanceof MockPaymentGateway mock)) {
            throw ApiException.notFound("Mock payments are disabled");
        }
        Payment payment = payments.findById(paymentId).orElseThrow(() -> ApiException.notFound("Payment not found"));
        if (!payment.getUserId().equals(caller.id())) {
            throw ApiException.forbidden("This payment belongs to another member");
        }
        return new MockSignatureResponse(payment.getProviderOrderId(), providerPaymentId,
                mock.sign(payment.getProviderOrderId(), providerPaymentId));
    }

    // ---------------------------------------------------------------- settlement

    private boolean settle(Payment payment, CapturedPayment captured) {
        if (captured.amountMinor() != Money.toMinor(payment.getAmount())
                || captured.currency() == null
                || !captured.currency().toUpperCase(Locale.ROOT).equals(payment.getCurrency())) {
            log.error("Not settling payment {}: provider reported {} {} (minor units) but {} {} was expected",
                    payment.getId(), captured.amountMinor(), captured.currency(), payment.getAmount(),
                    payment.getCurrency());
            return false;
        }
        markPaid(payment, captured.providerPaymentId(), () -> memberEmails.emailOf(payment.getUserId()));
        return true;
    }

    /**
     * Idempotent: a settled payment is left alone. Otherwise the payment succeeds and the registration is confirmed
     * — unless the event was cancelled, filled up meanwhile, or the registration is already paid by another
     * payment, in which case the money is refunded automatically.
     */
    private void markPaid(Payment payment, String providerPaymentId, Supplier<String> email) {
        if (payment.getStatus().isSettled()) {
            return;
        }
        if (providerPaymentId != null && !providerPaymentId.isBlank()) {
            payment.setProviderPaymentId(providerPaymentId);
        }
        transition(payment, PaymentStatus.SUCCEEDED);

        Registration registration = registrations.findById(payment.getRegistrationId())
                .orElseThrow(() -> ApiException.notFound("Registration not found"));
        // lock the event row: the capacity check below must not race with registrations
        Event event = events.findByIdForUpdate(payment.getEventId())
                .orElseThrow(() -> ApiException.notFound("Event not found"));
        if (registration.hasTicket()) {
            boolean paidTwice = payments.findByRegistrationId(registration.getId()).stream()
                    .anyMatch(p -> !p.getId().equals(payment.getId()) && p.getStatus().isSettled()
                            && p.getStatus() != PaymentStatus.REFUNDED);
            if (paidTwice) {
                issueRefund(payment, event, REASON_DUPLICATE);
            }
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (event.getStatus() == EventStatus.CANCELLED) {
            cancelRegistration(registration);
            issueRefund(payment, event, REASON_EVENT_CANCELLED);
            return;
        }
        if (event.getCapacity() != null && registrations.countSpotsTaken(event.getId(), registration.getUserId(),
                now.minus(pendingHold())) >= event.getCapacity()) {
            cancelRegistration(registration);
            issueRefund(payment, event, REASON_EVENT_FULL);
            return;
        }
        ticketIssuer.confirm(registration, now);
        log.info("Registration {} confirmed by payment {}", registration.getId(), payment.getId());
        notifier.registrationConfirmedLazily(registration.getUserId(), email, event, registration.getTicketCode());
    }

    private void issueRefund(Payment payment, Event event, String reason) {
        payment.setRefundReason(truncate(reason));
        transition(payment, PaymentStatus.REFUND_PENDING);
        callRefund(payment);
        UUID userId = payment.getUserId();
        notifier.paymentRefunded(userId, () -> memberEmails.emailOf(userId), event, payment.getAmount(),
                payment.getCurrency(), reason);
    }

    /** Calls the provider; on failure the payment stays REFUND_PENDING for a later retry. */
    private boolean callRefund(Payment payment) {
        try {
            String refundId = gateway.refund(payment.getProviderPaymentId(), payment.getAmount(),
                    payment.getRefundReason());
            payment.setRefundId(refundId);
            payment.setRefundedAt(LocalDateTime.now(clock));
            transition(payment, PaymentStatus.REFUNDED);
            return true;
        } catch (RuntimeException e) {
            log.warn("Refund of payment {} failed, will retry: {}", payment.getId(), e.getMessage());
            return false;
        }
    }

    private static void cancelRegistration(Registration registration) {
        registration.setStatus(RegistrationStatus.CANCELLED);
        registration.setTicketCode(null);
    }

    private void transition(Payment payment, PaymentStatus to) {
        log.info("Payment {} {} -> {}", payment.getId(), payment.getStatus(), to);
        payment.setStatus(to);
        BusinessMetrics.increment("payments.transitions", "status", to.name().toLowerCase(java.util.Locale.ROOT),
                "provider", String.valueOf(payment.getProvider()).toLowerCase(java.util.Locale.ROOT));
    }

    private RegistrationStatus registrationStatus(Payment payment) {
        return registrations.findById(payment.getRegistrationId()).map(Registration::getStatus).orElse(null);
    }

    private static String truncate(String value) {
        return value != null && value.length() > 255 ? value.substring(0, 255) : value;
    }
}
