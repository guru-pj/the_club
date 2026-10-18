package com.theclub.eventservice.service;

import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import com.theclub.eventservice.client.ClubClient.ClubInfo;
import com.theclub.eventservice.dto.CreateEventRequest;
import com.theclub.eventservice.dto.EventResponse;
import com.theclub.eventservice.dto.UpdateEventRequest;
import com.theclub.eventservice.model.Event;
import com.theclub.eventservice.model.EventStatus;
import com.theclub.eventservice.model.Registration;
import com.theclub.eventservice.model.RegistrationStatus;
import com.theclub.eventservice.repository.EventRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.theclub.eventservice.service.TestData.CLOCK;
import static com.theclub.eventservice.service.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventServiceTest {

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

    EventService service;
    final UUID clubId = UUID.randomUUID();
    final UUID ownerId = UUID.randomUUID();
    final AuthenticatedUser owner = TestData.user(ownerId, "USER", "CLUB_OWNER");

    @BeforeEach
    void setUp() {
        service = new EventService(events, registrations, clubAccess, notifier, paymentService, CLOCK);
        when(paymentService.pendingHold()).thenReturn(java.time.Duration.ofMinutes(15));
        when(events.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));
        when(clubAccess.requireOwnerOrAdmin(eq(clubId), any())).thenReturn(new ClubInfo(clubId, "Chennai Runners", ownerId, 3));
    }

    private CreateEventRequest request(LocalDateTime start, LocalDateTime opens, LocalDateTime closes) {
        return new CreateEventRequest(clubId, "Sunday Long Run", null, "Marina", start, opens, closes, 50,
                null, null, null);
    }

    @Test
    void createAppliesDefaults() {
        EventResponse created = service.create(request(NOW.plusDays(3), null, null), owner);

        assertThat(created.registrationOpensAt()).isEqualTo(NOW);
        assertThat(created.registrationClosesAt()).isEqualTo(NOW.plusDays(3));
        assertThat(created.price()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(created.currency()).isEqualTo("INR");
        assertThat(created.points()).isEqualTo(10);
        assertThat(created.status()).isEqualTo(EventStatus.SCHEDULED);
        assertThat(created.registrationOpen()).isTrue();
        assertThat(created.spotsLeft()).isEqualTo(50);
    }

    @Test
    void createRejectsInconsistentSchedules() {
        assertBadRequest(() -> service.create(request(NOW.minusHours(1), NOW.minusDays(1), null), owner));
        assertBadRequest(() -> service.create(request(NOW.plusDays(3), NOW.plusDays(4), null), owner));
        assertBadRequest(() -> service.create(request(NOW.plusDays(3), NOW, NOW.plusDays(4)), owner));
        assertBadRequest(() -> service.create(request(NOW.plusDays(3), NOW.plusDays(1), NOW.plusHours(1)), owner));
    }

    @Test
    void createRequiresClubOwner() {
        AuthenticatedUser stranger = TestData.user(UUID.randomUUID());
        when(clubAccess.requireOwnerOrAdmin(clubId, stranger)).thenThrow(ApiException.forbidden("no"));

        assertThatThrownBy(() -> service.create(request(NOW.plusDays(3), null, null), stranger))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void capacityCannotDropBelowTakenSpots() {
        Event event = TestData.event(clubId, ownerId);
        when(events.findById(event.getId())).thenReturn(Optional.of(event));
        when(registrations.countSpotsTaken(eq(event.getId()), any(), any())).thenReturn(5L);

        UpdateEventRequest update = new UpdateEventRequest(null, null, null, null, null, null, 4, null, null, null);
        assertThatThrownBy(() -> service.update(event.getId(), update, owner))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void cancelNotifiesActiveRegistrants() {
        Event event = TestData.event(clubId, ownerId);
        Registration confirmed = new Registration(event.getId(), UUID.randomUUID());
        confirmed.setStatus(RegistrationStatus.CONFIRMED);
        when(events.findById(event.getId())).thenReturn(Optional.of(event));
        when(registrations.findByEventIdAndStatusIn(eq(event.getId()), anyCollection())).thenReturn(List.of(confirmed));

        EventResponse cancelled = service.cancel(event.getId(), owner);

        assertThat(cancelled.status()).isEqualTo(EventStatus.CANCELLED);
        assertThat(cancelled.registrationOpen()).isFalse();
        verify(notifier).eventCancelled(event, List.of(confirmed.getUserId()));
        assertThatThrownBy(() -> service.cancel(event.getId(), owner)).isInstanceOf(ApiException.class);
    }

    private static void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
