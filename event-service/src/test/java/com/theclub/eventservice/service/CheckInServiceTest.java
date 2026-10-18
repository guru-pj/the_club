package com.theclub.eventservice.service;

import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import com.theclub.eventservice.config.CheckInProperties;
import com.theclub.eventservice.dto.CheckInResponse;
import com.theclub.eventservice.model.Event;
import com.theclub.eventservice.model.EventStatus;
import com.theclub.eventservice.model.PointsLedgerEntry;
import com.theclub.eventservice.model.Registration;
import com.theclub.eventservice.model.RegistrationStatus;
import com.theclub.eventservice.repository.EventRepository;
import com.theclub.eventservice.repository.PaymentRepository;
import com.theclub.eventservice.repository.PointsLedgerRepository;
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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.theclub.eventservice.service.TestData.CLOCK;
import static com.theclub.eventservice.service.TestData.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckInServiceTest {

    @Mock
    EventRepository events;
    @Mock
    RegistrationRepository registrations;
    @Mock
    PointsLedgerRepository ledger;
    @Mock
    PaymentRepository payments;
    @Mock
    ClubAccess clubAccess;
    @Mock
    NameResolver names;

    CheckInService service;
    final UUID clubId = UUID.randomUUID();
    final UUID ownerId = UUID.randomUUID();
    final AuthenticatedUser owner = TestData.user(ownerId, "USER", "CLUB_OWNER");
    Event event;
    Registration registration;

    @BeforeEach
    void setUp() {
        service = new CheckInService(events, registrations, ledger, payments, clubAccess, names, new CheckInProperties(null, null), CLOCK);
        event = TestData.event(clubId, ownerId);
        event.setStartTime(NOW.plusHours(1));
        event.setPoints(15);
        registration = new Registration(event.getId(), UUID.randomUUID());
        new TicketIssuer().confirm(registration, NOW.minusDays(1));
        when(events.findById(event.getId())).thenReturn(Optional.of(event));
        when(registrations.findByTicketCode(registration.getTicketCode())).thenReturn(Optional.of(registration));
        when(names.userNames(anyCollection())).thenReturn(Map.of(registration.getUserId(), "Asha"));
    }

    @Test
    void checkInWithScannedPayloadAwardsPoints() {
        CheckInResponse response = service.checkIn(event.getId(), QrCodeService.payload(registration.getTicketCode()), owner);

        assertThat(response.pointsAwarded()).isEqualTo(15);
        assertThat(response.name()).isEqualTo("Asha");
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CHECKED_IN);
        assertThat(registration.getCheckedInBy()).isEqualTo(ownerId);
        ArgumentCaptor<PointsLedgerEntry> entry = ArgumentCaptor.forClass(PointsLedgerEntry.class);
        verify(ledger).save(entry.capture());
        assertThat(entry.getValue().getClubId()).isEqualTo(clubId);
        assertThat(entry.getValue().getPoints()).isEqualTo(15);
        assertThat(entry.getValue().getAwardedAt()).isEqualTo(NOW);
    }

    @Test
    void secondScanIsRejected() {
        service.checkIn(event.getId(), registration.getTicketCode(), owner);

        assertThatThrownBy(() -> service.checkIn(event.getId(), registration.getTicketCode(), owner))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("already checked in");
                });
    }

    @Test
    void pointsAreNotAwardedTwice() {
        when(ledger.existsByUserIdAndEventId(registration.getUserId(), event.getId())).thenReturn(true);

        assertThat(service.checkIn(event.getId(), registration.getTicketCode(), owner).pointsAwarded()).isZero();
        verify(ledger, never()).save(any());
    }

    @Test
    void ticketFromAnotherEventIsNotFound() {
        Event other = TestData.event(clubId, ownerId);
        other.setStartTime(NOW.plusHours(1));
        when(events.findById(other.getId())).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.checkIn(other.getId(), registration.getTicketCode(), owner))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void checkInOnlyDuringEventWindow() {
        event.setStartTime(NOW.plusHours(7));
        assertConflict(() -> service.checkIn(event.getId(), registration.getTicketCode(), owner));

        event.setStartTime(NOW.minusHours(13));
        assertConflict(() -> service.checkIn(event.getId(), registration.getTicketCode(), owner));

        event.setStartTime(NOW.minusHours(11));
        assertThat(service.checkIn(event.getId(), registration.getTicketCode(), owner).pointsAwarded()).isEqualTo(15);
    }

    @Test
    void cancelledEventOrUnconfirmedTicketCannotCheckIn() {
        registration.setStatus(RegistrationStatus.CANCELLED);
        assertConflict(() -> service.checkIn(event.getId(), registration.getTicketCode(), owner));

        registration.setStatus(RegistrationStatus.CONFIRMED);
        event.setStatus(EventStatus.CANCELLED);
        assertConflict(() -> service.checkIn(event.getId(), registration.getTicketCode(), owner));
    }

    @Test
    void onlyOwnerCanCheckIn() {
        AuthenticatedUser member = TestData.user(UUID.randomUUID());
        when(clubAccess.requireOwnerOrAdmin(eq(clubId), eq(member))).thenThrow(ApiException.forbidden("no"));

        assertThatThrownBy(() -> service.checkIn(event.getId(), registration.getTicketCode(), member))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private static void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }
}
