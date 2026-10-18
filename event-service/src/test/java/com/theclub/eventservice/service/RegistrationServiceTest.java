package com.theclub.eventservice.service;

import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import com.theclub.eventservice.dto.RegisterResponse;
import com.theclub.eventservice.model.Event;
import com.theclub.eventservice.model.Payment;
import com.theclub.eventservice.model.PaymentProvider;
import com.theclub.eventservice.model.Registration;
import com.theclub.eventservice.model.RegistrationStatus;
import com.theclub.eventservice.repository.EventRepository;
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
import java.util.Optional;
import java.util.UUID;

import static com.theclub.eventservice.service.TestData.CLOCK;
import static com.theclub.eventservice.service.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistrationServiceTest {

    @Mock
    EventRepository events;
    @Mock
    RegistrationRepository registrations;
    @Mock
    ClubAccess clubAccess;
    @Mock
    PaymentService paymentService;
    @Mock
    ConfirmationNotifier notifier;

    RegistrationService service;
    final UUID clubId = UUID.randomUUID();
    final UUID memberId = UUID.randomUUID();
    final AuthenticatedUser member = TestData.user(memberId);
    Event event;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(events, registrations, clubAccess, paymentService, new TicketIssuer(),
                notifier, new QrCodeService(), CLOCK);
        event = TestData.event(clubId, UUID.randomUUID());
        when(events.findById(event.getId())).thenReturn(Optional.of(event));
        when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(clubAccess.isMember(clubId, memberId)).thenReturn(true);
        when(registrations.findByEventIdAndUserId(event.getId(), memberId)).thenReturn(Optional.empty());
        when(registrations.save(any(Registration.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void freeEventConfirmsImmediatelyWithTicketAndNotification() {
        RegisterResponse response = service.register(event.getId(), member);

        assertThat(response.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(response.payment()).isNull();
        ArgumentCaptor<Registration> saved = ArgumentCaptor.forClass(Registration.class);
        verify(registrations).save(saved.capture());
        assertThat(saved.getValue().getTicketCode()).hasSize(32).matches("[A-Za-z0-9_-]+");
        assertThat(saved.getValue().getConfirmedAt()).isEqualTo(NOW);
        verify(notifier).registrationConfirmed(memberId, member.email(), event, saved.getValue().getTicketCode());
    }

    @Test
    void paidEventWaitsForPayment() {
        event.setPrice(new BigDecimal("499.00"));
        when(paymentService.createFor(any(), eq(event))).thenAnswer(inv ->
                new Payment(inv.getArgument(0), event.getPrice(), "INR", PaymentProvider.MOCK, "mock_order_1"));
        when(paymentService.providerKeyId()).thenReturn("mock");

        RegisterResponse response = service.register(event.getId(), member);

        assertThat(response.status()).isEqualTo(RegistrationStatus.PENDING_PAYMENT);
        assertThat(response.payment().providerOrderId()).isEqualTo("mock_order_1");
        assertThat(response.payment().amount()).isEqualByComparingTo("499.00");
        verify(notifier, never()).registrationConfirmed(any(), any(), any(), anyString());
    }

    @Test
    void nonMembersCannotRegister() {
        when(clubAccess.isMember(clubId, memberId)).thenReturn(false);

        assertStatus(() -> service.register(event.getId(), member), HttpStatus.FORBIDDEN, "Join the club to register");
    }

    @Test
    void registrationWindowIsEnforced() {
        event.setRegistrationOpensAt(NOW.plusHours(2));
        assertStatus(() -> service.register(event.getId(), member), HttpStatus.CONFLICT, "Registration opens at");

        event.setRegistrationOpensAt(NOW.minusDays(2));
        event.setRegistrationClosesAt(NOW);
        assertStatus(() -> service.register(event.getId(), member), HttpStatus.CONFLICT, "Registration is closed");
    }

    @Test
    void fullEventRejectsRegistration() {
        event.setCapacity(2);
        when(registrations.countSpotsTaken(eq(event.getId()), eq(memberId), eq(NOW.minusMinutes(15)))).thenReturn(2L);

        assertStatus(() -> service.register(event.getId(), member), HttpStatus.CONFLICT, "Event is full");
    }

    @Test
    void duplicateRegistrationRejectedButCancelledOneIsReused() {
        Registration existing = new Registration(event.getId(), memberId);
        existing.setStatus(RegistrationStatus.CONFIRMED);
        when(registrations.findByEventIdAndUserId(event.getId(), memberId)).thenReturn(Optional.of(existing));
        assertStatus(() -> service.register(event.getId(), member), HttpStatus.CONFLICT, "already registered");

        existing.setStatus(RegistrationStatus.CANCELLED);
        RegisterResponse response = service.register(event.getId(), member);
        assertThat(response.registrationId()).isEqualTo(existing.getId());
        assertThat(response.status()).isEqualTo(RegistrationStatus.CONFIRMED);
    }

    @Test
    void paidConfirmedRegistrationCannotBeSelfCancelled() {
        event.setPrice(new BigDecimal("100"));
        Registration existing = new Registration(event.getId(), memberId);
        existing.setStatus(RegistrationStatus.CONFIRMED);
        when(registrations.findByEventIdAndUserId(event.getId(), memberId)).thenReturn(Optional.of(existing));

        assertStatus(() -> service.cancelOwn(event.getId(), member), HttpStatus.CONFLICT, "refunds");

        event.setPrice(BigDecimal.ZERO);
        existing.setTicketCode("abc");
        service.cancelOwn(event.getId(), member);
        assertThat(existing.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(existing.getTicketCode()).isNull();
    }

    @Test
    void ticketOnlyVisibleToOwnerClubOwnerOrAdmin() {
        Registration registration = new Registration(event.getId(), memberId);
        new TicketIssuer().confirm(registration, NOW);
        when(registrations.findById(registration.getId())).thenReturn(Optional.of(registration));
        AuthenticatedUser stranger = TestData.user(UUID.randomUUID());
        when(clubAccess.isOwnerOrAdmin(clubId, stranger)).thenReturn(false);

        assertThat(service.ticket(registration.getId(), member).qrCodeBase64()).isNotBlank();
        assertStatus(() -> service.ticket(registration.getId(), stranger), HttpStatus.FORBIDDEN, "another member");
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status,
                                     String messagePart) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getMessage()).contains(messagePart);
        });
    }
}
