package com.theclub.eventservice.service;

import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.security.Roles;
import com.theclub.common.web.ApiException;
import com.theclub.eventservice.dto.ConfirmPaymentRequest;
import com.theclub.eventservice.dto.PaymentResponse;
import com.theclub.eventservice.model.Event;
import com.theclub.eventservice.model.EventStatus;
import com.theclub.eventservice.model.Payment;
import com.theclub.eventservice.model.PaymentProvider;
import com.theclub.eventservice.model.PaymentStatus;
import com.theclub.eventservice.model.Registration;
import com.theclub.eventservice.model.RegistrationStatus;
import com.theclub.eventservice.payment.CapturedPayment;
import com.theclub.eventservice.payment.MockPaymentGateway;
import com.theclub.eventservice.payment.PaymentProperties;
import com.theclub.eventservice.repository.EventRepository;
import com.theclub.eventservice.repository.PaymentRepository;
import com.theclub.eventservice.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static com.theclub.eventservice.service.TestData.CLOCK;
import static com.theclub.eventservice.service.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock
    PaymentRepository payments;
    @Mock
    RegistrationRepository registrations;
    @Mock
    EventRepository events;
    @Mock
    ConfirmationNotifier notifier;
    @Mock
    MemberEmails memberEmails;
    @Mock
    ClubAccess clubAccess;

    final MockPaymentGateway gateway = spy(new MockPaymentGateway("secret"));
    final PaymentProperties properties = new PaymentProperties("mock", "secret", null, null, false, null, null);
    PaymentService service;
    final UUID memberId = UUID.randomUUID();
    final UUID ownerId = UUID.randomUUID();
    final AuthenticatedUser member = TestData.user(memberId);
    final AuthenticatedUser owner = TestData.user(ownerId, Roles.USER, Roles.CLUB_OWNER);
    final List<Payment> registrationPayments = new ArrayList<>();
    Registration registration;
    Payment payment;
    Event event;

    @BeforeEach
    void setUp() {
        service = new PaymentService(payments, registrations, events, gateway, new TicketIssuer(), notifier,
                memberEmails, clubAccess, properties, CLOCK);
        event = TestData.event(UUID.randomUUID(), ownerId);
        event.setPrice(new BigDecimal("250"));
        event.setCurrency("INR");
        registration = new Registration(event.getId(), memberId);
        registration.setStatus(RegistrationStatus.PENDING_PAYMENT);
        registration.setRegisteredAt(NOW.minusMinutes(1));
        payment = new Payment(registration, event.getPrice(), "INR", PaymentProvider.MOCK,
                gateway.createOrder(event.getPrice(), "INR", "r"));
        payment.setCreatedAt(NOW.minusMinutes(1));
        registrationPayments.add(payment);
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(payments.findByProviderOrderIdForUpdate(payment.getProviderOrderId())).thenReturn(Optional.of(payment));
        when(payments.findByRegistrationId(registration.getId())).thenReturn(registrationPayments);
        when(registrations.findById(registration.getId())).thenReturn(Optional.of(registration));
        when(events.findById(event.getId())).thenReturn(Optional.of(event));
        when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(memberEmails.emailOf(memberId)).thenReturn("member@example.com");
    }

    private long paise() {
        return 25_000;
    }

    @SuppressWarnings("unchecked")
    private String emailPassedToConfirmation() {
        ArgumentCaptor<Supplier<String>> email = ArgumentCaptor.forClass(Supplier.class);
        verify(notifier).registrationConfirmedLazily(eq(memberId), email.capture(), eq(event), anyString());
        return email.getValue().get();
    }

    // ---------------------------------------------------------------- client confirm

    @Test
    void validSignatureConfirmsRegistrationAndIsIdempotent() {
        String signature = gateway.sign(payment.getProviderOrderId(), "pay_1");

        PaymentResponse response = service.confirm(payment.getId(), new ConfirmPaymentRequest("pay_1", signature), member);
        service.confirm(payment.getId(), new ConfirmPaymentRequest("pay_1", signature), member);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(response.registrationStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registration.getTicketCode()).isNotNull();
        assertThat(emailPassedToConfirmation()).isEqualTo(member.email());
    }

    @Test
    void invalidSignatureFailsPaymentButALaterValidSignatureStillSettles() {
        assertThatThrownBy(() -> service.confirm(payment.getId(), new ConfirmPaymentRequest("pay_1", "deadbeef"), member))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.PENDING_PAYMENT);
        verify(notifier, never()).registrationConfirmedLazily(any(), any(), any(), anyString());

        // the signature proves the provider took the money (e.g. a retry in the same checkout)
        service.confirm(payment.getId(),
                new ConfirmPaymentRequest("pay_2", gateway.sign(payment.getProviderOrderId(), "pay_2")), member);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderPaymentId()).isEqualTo("pay_2");
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
    }

    @Test
    void onlyThePayerCanConfirm() {
        AuthenticatedUser other = TestData.user(UUID.randomUUID());
        String signature = gateway.sign(payment.getProviderOrderId(), "pay_1");

        assertThatThrownBy(() -> service.confirm(payment.getId(), new ConfirmPaymentRequest("pay_1", signature), other))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void mockSignatureMatchesGateway() {
        assertThat(service.mockSignature(payment.getId(), "pay_9", member).signature())
                .isEqualTo(gateway.sign(payment.getProviderOrderId(), "pay_9"));
    }

    // ---------------------------------------------------------------- provider-reported captures

    @Test
    void capturedOrderConfirmsRegistrationAndEmailsTheMember() {
        assertThat(service.settleCapturedOrder(payment.getProviderOrderId(), "pay_w", paise(), "inr")).isTrue();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderPaymentId()).isEqualTo("pay_w");
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(emailPassedToConfirmation()).isEqualTo("member@example.com");

        // a second delivery is a no-op
        service.settleCapturedOrder(payment.getProviderOrderId(), "pay_w", paise(), "INR");
        verify(notifier, times(1)).registrationConfirmedLazily(eq(memberId), any(), any(), anyString());
    }

    @Test
    void amountOrCurrencyMismatchIsNotSettled() {
        assertThat(service.settleCapturedOrder(payment.getProviderOrderId(), "pay_w", 100, "INR")).isFalse();
        assertThat(service.settleCapturedOrder(payment.getProviderOrderId(), "pay_w", paise(), "USD")).isFalse();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.PENDING_PAYMENT);
    }

    @Test
    void unknownOrderIsIgnored() {
        assertThat(service.settleCapturedOrder("order_unknown", "pay_x", paise(), "INR")).isFalse();
    }

    @Test
    void failedAttemptMarksOnlyFreshPaymentsFailed() {
        service.markFailedByOrder(payment.getProviderOrderId());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        payment.setStatus(PaymentStatus.SUCCEEDED);
        service.markFailedByOrder(payment.getProviderOrderId());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    // ---------------------------------------------------------------- expiry and late captures

    @Test
    void expiringReleasesTheRegistration() {
        assertThat(service.expire(payment.getId())).isTrue();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(service.expire(payment.getId())).isFalse();
    }

    @Test
    void expiringKeepsRegistrationWhenANewerAttemptIsPending() {
        Payment newer = new Payment(registration, event.getPrice(), "INR", PaymentProvider.MOCK, "mock_order_newer");
        registrationPayments.add(newer);

        service.expire(payment.getId());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.PENDING_PAYMENT);
    }

    @Test
    void lateCaptureAfterExpiryReconfirmsWhenThereIsRoom() {
        service.expire(payment.getId());
        event.setCapacity(10);
        when(registrations.countSpotsTaken(eq(event.getId()), eq(memberId), any())).thenReturn(3L);

        service.settleCaptured(payment.getId(), new CapturedPayment("pay_late", paise(), "INR"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
    }

    @Test
    void lateCaptureWhenEventIsFullIsRefunded() {
        service.expire(payment.getId());
        event.setCapacity(10);
        when(registrations.countSpotsTaken(eq(event.getId()), eq(memberId), any())).thenReturn(10L);

        service.settleCaptured(payment.getId(), new CapturedPayment("pay_late", paise(), "INR"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundId()).startsWith("mock_refund_");
        assertThat(payment.getRefundReason()).isEqualTo(PaymentService.REASON_EVENT_FULL);
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        verify(notifier).paymentRefunded(eq(memberId), any(), eq(event), eq(payment.getAmount()), eq("INR"),
                eq(PaymentService.REASON_EVENT_FULL));
        verify(notifier, never()).registrationConfirmedLazily(any(), any(), any(), anyString());
    }

    @Test
    void captureForCancelledEventIsRefunded() {
        event.setStatus(EventStatus.CANCELLED);

        service.settleCaptured(payment.getId(), new CapturedPayment("pay_c", paise(), "INR"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
    }

    @Test
    void secondPaymentForAnAlreadyPaidRegistrationIsRefunded() {
        Payment first = new Payment(registration, event.getPrice(), "INR", PaymentProvider.MOCK, "mock_order_first");
        first.setStatus(PaymentStatus.SUCCEEDED);
        registrationPayments.add(first);
        new TicketIssuer().confirm(registration, NOW);

        service.settleCaptured(payment.getId(), new CapturedPayment("pay_dup", paise(), "INR"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundReason()).isEqualTo(PaymentService.REASON_DUPLICATE);
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
    }

    // ---------------------------------------------------------------- refunds

    private void paid() {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setProviderPaymentId("pay_ok");
        new TicketIssuer().confirm(registration, NOW);
    }

    @Test
    void ownerRefundsAPaymentAndTheRegistrationIsCancelled() {
        paid();

        PaymentResponse response = service.refund(payment.getId(), "Injured", owner);

        assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(response.refundReason()).isEqualTo("Injured");
        assertThat(response.refundedAt()).isNotNull();
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(registration.getTicketCode()).isNull();
        verify(clubAccess).requireOwnerOrAdmin(event.getClubId(), owner);
        verify(notifier).paymentRefunded(eq(memberId), any(), eq(event), any(), eq("INR"), eq("Injured"));
    }

    @Test
    void refundRequiresClubOwnerOrAdmin() {
        paid();
        when(clubAccess.requireOwnerOrAdmin(event.getClubId(), member))
                .thenThrow(ApiException.forbidden("Only the club owner can manage this event"));

        assertThatThrownBy(() -> service.refund(payment.getId(), null, member))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void checkedInRegistrationsCannotBeRefunded() {
        paid();
        registration.setStatus(RegistrationStatus.CHECKED_IN);

        assertThatThrownBy(() -> service.refund(payment.getId(), null, owner))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("Cannot refund a checked-in registration");
                });
    }

    @Test
    void onlySuccessfulPaymentsCanBeRefunded() {
        assertThatThrownBy(() -> service.refund(payment.getId(), null, owner))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void providerFailureLeavesRefundPendingUntilRetried() {
        paid();
        doThrow(new IllegalStateException("provider down")).when(gateway).refund(anyString(), any(), any());

        PaymentResponse response = service.refund(payment.getId(), null, owner);
        assertThat(response.status()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(payment.getRefundReason()).isEqualTo(PaymentService.REASON_BY_CLUB);

        org.mockito.Mockito.reset(gateway);
        assertThat(service.retryRefund(payment.getId())).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(service.retryRefund(payment.getId())).isFalse();
    }

    @Test
    void refundProcessedWebhookCompletesAPendingRefund() {
        paid();
        payment.setStatus(PaymentStatus.REFUND_PENDING);
        when(payments.findByProviderPaymentId("pay_ok")).thenReturn(Optional.of(payment));

        service.markRefundProcessed("pay_ok", "rfnd_1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundId()).isEqualTo("rfnd_1");
    }

    @Test
    void cancellingAnEventRefundsEverySuccessfulPayment() {
        paid();
        Registration checkedIn = new Registration(event.getId(), UUID.randomUUID());
        checkedIn.setStatus(RegistrationStatus.CHECKED_IN);
        Payment checkedInPayment = new Payment(checkedIn, event.getPrice(), "INR", PaymentProvider.MOCK, "mock_order_ci");
        checkedInPayment.setStatus(PaymentStatus.SUCCEEDED);
        when(payments.findByEventIdAndStatus(event.getId(), PaymentStatus.SUCCEEDED))
                .thenReturn(List.of(payment, checkedInPayment));
        when(payments.findByIdForUpdate(checkedInPayment.getId())).thenReturn(Optional.of(checkedInPayment));
        when(registrations.findById(checkedIn.getId())).thenReturn(Optional.of(checkedIn));

        assertThat(service.refundAllForCancelledEvent(event)).isEqualTo(1);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundReason()).isEqualTo(PaymentService.REASON_EVENT_CANCELLED);
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(checkedInPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }
}
