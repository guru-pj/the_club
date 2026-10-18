package com.theclub.eventservice.service;

import com.theclub.common.observability.BusinessMetrics;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import com.theclub.eventservice.config.CheckInProperties;
import com.theclub.eventservice.dto.CheckInResponse;
import com.theclub.eventservice.dto.EventRegistrationRow;
import com.theclub.eventservice.model.Event;
import com.theclub.eventservice.model.Payment;
import com.theclub.eventservice.model.EventStatus;
import com.theclub.eventservice.model.PointsLedgerEntry;
import com.theclub.eventservice.model.Registration;
import com.theclub.eventservice.model.RegistrationStatus;
import com.theclub.eventservice.repository.EventRepository;
import com.theclub.eventservice.repository.PaymentRepository;
import com.theclub.eventservice.repository.PointsLedgerRepository;
import com.theclub.eventservice.repository.RegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Event-day check-in by scanning the member's QR ticket; awards participation points exactly once.
 */
@Service
public class CheckInService {

    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final PointsLedgerRepository ledger;
    private final PaymentRepository payments;
    private final ClubAccess clubAccess;
    private final NameResolver names;
    private final CheckInProperties properties;
    private final Clock clock;

    public CheckInService(EventRepository events, RegistrationRepository registrations, PointsLedgerRepository ledger,
                          PaymentRepository payments, ClubAccess clubAccess, NameResolver names,
                          CheckInProperties properties, Clock clock) {
        this.events = events;
        this.payments = payments;
        this.registrations = registrations;
        this.ledger = ledger;
        this.clubAccess = clubAccess;
        this.names = names;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public CheckInResponse checkIn(UUID eventId, String scannedCode, AuthenticatedUser caller) {
        Event event = events.findById(eventId).orElseThrow(() -> ApiException.notFound("Event not found"));
        clubAccess.requireOwnerOrAdmin(event.getClubId(), caller);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw ApiException.conflict("Event is cancelled");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime opens = event.getStartTime().minus(properties.opensBefore());
        LocalDateTime closes = event.getStartTime().plus(properties.closesAfter());
        if (now.isBefore(opens) || now.isAfter(closes)) {
            throw ApiException.conflict("Check-in is open from " + opens.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    + " to " + closes.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        String code = QrCodeService.extractCode(scannedCode);
        Registration registration = registrations.findByTicketCode(code)
                .filter(r -> r.getEventId().equals(eventId))
                .orElseThrow(() -> ApiException.notFound("Ticket not found for this event"));
        if (registration.getStatus() == RegistrationStatus.CHECKED_IN) {
            throw ApiException.conflict("Ticket already checked in at "
                    + registration.getCheckedInAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (registration.getStatus() != RegistrationStatus.CONFIRMED) {
            throw ApiException.conflict("Ticket is not valid: registration is " + registration.getStatus());
        }
        registration.setStatus(RegistrationStatus.CHECKED_IN);
        registration.setCheckedInAt(now);
        registration.setCheckedInBy(caller.id());
        int awarded = 0;
        if (!ledger.existsByUserIdAndEventId(registration.getUserId(), eventId)) {
            ledger.save(new PointsLedgerEntry(registration.getUserId(), event.getClubId(), eventId, event.getPoints(),
                    PointsLedgerEntry.REASON_EVENT_CHECK_IN, now));
            awarded = event.getPoints();
        }
        BusinessMetrics.increment("events.checkins");
        BusinessMetrics.add("events.points_awarded", awarded);
        String name = names.userNames(List.of(registration.getUserId())).get(registration.getUserId());
        return new CheckInResponse(registration.getId(), registration.getUserId(), name, now, awarded);
    }

    @Transactional(readOnly = true)
    public List<EventRegistrationRow> registrations(UUID eventId, AuthenticatedUser caller) {
        Event event = events.findById(eventId).orElseThrow(() -> ApiException.notFound("Event not found"));
        clubAccess.requireOwnerOrAdmin(event.getClubId(), caller);
        List<Registration> rows = registrations.findByEventIdOrderByRegisteredAt(eventId);
        Map<UUID, String> userNames = names.userNames(rows.stream().map(Registration::getUserId).toList());
        Map<UUID, Payment> latestPayment = payments.findByEventId(eventId).stream()
                .collect(Collectors.toMap(Payment::getRegistrationId, Function.identity(),
                        (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b));
        return rows.stream()
                .map(r -> {
                    Payment payment = latestPayment.get(r.getId());
                    return new EventRegistrationRow(r.getId(), r.getUserId(), userNames.get(r.getUserId()),
                            r.getStatus(), r.getRegisteredAt(), r.getCheckedInAt(),
                            payment == null ? null : payment.getId(), payment == null ? null : payment.getStatus());
                })
                .toList();
    }
}
