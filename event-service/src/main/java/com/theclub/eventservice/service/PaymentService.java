package com.theclub.eventservice.service;

import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import com.theclub.eventservice.dto.ConfirmPaymentRequest;
import com.theclub.eventservice.dto.MockSignatureResponse;
import com.theclub.eventservice.dto.PaymentResponse;
import com.theclub.eventservice.model.Event;
import com.theclub.eventservice.model.Payment;
import com.theclub.eventservice.model.PaymentStatus;
import com.theclub.eventservice.model.Registration;
import com.theclub.eventservice.model.RegistrationStatus;
import com.theclub.eventservice.payment.MockPaymentGateway;
import com.theclub.eventservice.payment.PaymentGateway;
import com.theclub.eventservice.repository.EventRepository;
import com.theclub.eventservice.repository.PaymentRepository;
import com.theclub.eventservice.repository.RegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final RegistrationRepository registrations;
    private final EventRepository events;
    private final PaymentGateway gateway;
    private final TicketIssuer ticketIssuer;
    private final ConfirmationNotifier notifier;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, RegistrationRepository registrations, EventRepository events,
                          PaymentGateway gateway, TicketIssuer ticketIssuer, ConfirmationNotifier notifier,
                          Clock clock) {
        this.payments = payments;
        this.registrations = registrations;
        this.events = events;
        this.gateway = gateway;
        this.ticketIssuer = ticketIssuer;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Transactional
    public Payment createFor(Registration registration, Event event) {
        String orderId = gateway.createOrder(event.getPrice(), event.getCurrency(), registration.getId().toString());
        return payments.save(new Payment(registration, event.getPrice(), event.getCurrency(), gateway.provider(), orderId));
    }

    public String providerKeyId() {
        return gateway.keyId();
    }

    /**
     * Verifies the provider signature; on success the registration is confirmed and the ticket sent.
     * A failed verification is persisted (no rollback) and reported as 400.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public PaymentResponse confirm(UUID paymentId, ConfirmPaymentRequest request, AuthenticatedUser caller) {
        Payment payment = ownPayment(paymentId, caller);
        Registration registration = registrations.findById(payment.getRegistrationId())
                .orElseThrow(() -> ApiException.notFound("Registration not found"));
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return PaymentResponse.of(payment, registration.getStatus());
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            throw ApiException.conflict("This payment has failed; register again to retry");
        }
        payment.setProviderPaymentId(request.providerPaymentId());
        if (!gateway.verify(payment.getProviderOrderId(), request.providerPaymentId(), request.signature())) {
            payment.setStatus(PaymentStatus.FAILED);
            throw ApiException.badRequest("Payment verification failed");
        }
        payment.setStatus(PaymentStatus.SUCCEEDED);
        if (!registration.hasTicket()) {
            // Money was taken, so honour the registration even if the hold expired or it was cancelled meanwhile.
            ticketIssuer.confirm(registration, LocalDateTime.now(clock));
            Event event = events.findById(registration.getEventId())
                    .orElseThrow(() -> ApiException.notFound("Event not found"));
            notifier.registrationConfirmed(registration.getUserId(), caller.email(), event, registration.getTicketCode());
        }
        return PaymentResponse.of(payment, registration.getStatus());
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID paymentId, AuthenticatedUser caller) {
        Payment payment = payments.findById(paymentId).orElseThrow(() -> ApiException.notFound("Payment not found"));
        if (!caller.isAdmin() && !payment.getUserId().equals(caller.id())) {
            throw ApiException.forbidden("This payment belongs to another member");
        }
        RegistrationStatus status = registrations.findById(payment.getRegistrationId())
                .map(Registration::getStatus).orElse(null);
        return PaymentResponse.of(payment, status);
    }

    /**
     * Development helper: signs a fake provider payment id the way checkout would. Only exists with the mock provider.
     */
    @Transactional(readOnly = true)
    public MockSignatureResponse mockSignature(UUID paymentId, String providerPaymentId, AuthenticatedUser caller) {
        if (!(gateway instanceof MockPaymentGateway mock)) {
            throw ApiException.notFound("Mock payments are disabled");
        }
        Payment payment = ownPayment(paymentId, caller);
        return new MockSignatureResponse(payment.getProviderOrderId(), providerPaymentId,
                mock.sign(payment.getProviderOrderId(), providerPaymentId));
    }

    private Payment ownPayment(UUID paymentId, AuthenticatedUser caller) {
        Payment payment = payments.findById(paymentId).orElseThrow(() -> ApiException.notFound("Payment not found"));
        if (!payment.getUserId().equals(caller.id())) {
            throw ApiException.forbidden("This payment belongs to another member");
        }
        return payment;
    }
}
