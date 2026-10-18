package com.theclub.eventservice.service;

import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import com.theclub.eventservice.dto.ConfirmPaymentRequest;
import com.theclub.eventservice.dto.PaymentResponse;
import com.theclub.eventservice.model.Event;
import com.theclub.eventservice.model.Payment;
import com.theclub.eventservice.model.PaymentProvider;
import com.theclub.eventservice.model.PaymentStatus;
import com.theclub.eventservice.model.Registration;
import com.theclub.eventservice.model.RegistrationStatus;
import com.theclub.eventservice.payment.MockPaymentGateway;
import com.theclub.eventservice.repository.EventRepository;
import com.theclub.eventservice.repository.PaymentRepository;
import com.theclub.eventservice.repository.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static com.theclub.eventservice.service.TestData.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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

    final MockPaymentGateway gateway = new MockPaymentGateway("secret");
    PaymentService service;
    final UUID memberId = UUID.randomUUID();
    final AuthenticatedUser member = TestData.user(memberId);
    Registration registration;
    Payment payment;
    Event event;

    @BeforeEach
    void setUp() {
        service = new PaymentService(payments, registrations, events, gateway, new TicketIssuer(), notifier, CLOCK);
        event = TestData.event(UUID.randomUUID(), UUID.randomUUID());
        event.setPrice(new BigDecimal("250"));
        registration = new Registration(event.getId(), memberId);
        registration.setStatus(RegistrationStatus.PENDING_PAYMENT);
        payment = new Payment(registration, event.getPrice(), "INR", PaymentProvider.MOCK,
                gateway.createOrder(event.getPrice(), "INR", "r"));
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(registrations.findById(registration.getId())).thenReturn(Optional.of(registration));
        when(events.findById(event.getId())).thenReturn(Optional.of(event));
    }

    @Test
    void validSignatureConfirmsRegistrationAndIsIdempotent() {
        String signature = gateway.sign(payment.getProviderOrderId(), "pay_1");

        PaymentResponse response = service.confirm(payment.getId(), new ConfirmPaymentRequest("pay_1", signature), member);
        service.confirm(payment.getId(), new ConfirmPaymentRequest("pay_1", signature), member);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(response.registrationStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registration.getTicketCode()).isNotNull();
        verify(notifier, times(1)).registrationConfirmed(memberId, member.email(), event, registration.getTicketCode());
    }

    @Test
    void invalidSignatureFailsPayment() {
        assertThatThrownBy(() -> service.confirm(payment.getId(), new ConfirmPaymentRequest("pay_1", "deadbeef"), member))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.PENDING_PAYMENT);
        verify(notifier, never()).registrationConfirmed(any(), any(), any(), anyString());
        assertThatThrownBy(() -> service.confirm(payment.getId(),
                new ConfirmPaymentRequest("pay_1", gateway.sign(payment.getProviderOrderId(), "pay_1")), member))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
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
}
