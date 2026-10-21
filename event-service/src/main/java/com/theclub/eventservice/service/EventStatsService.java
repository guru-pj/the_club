package com.theclub.eventservice.service;

import com.theclub.common.security.AuthenticatedUser;
import com.theclub.eventservice.dto.EventStats;
import com.theclub.eventservice.model.Event;
import com.theclub.eventservice.model.EventStatus;
import com.theclub.eventservice.model.RegistrationStatus;
import com.theclub.eventservice.repository.EventRepository;
import com.theclub.eventservice.repository.PaymentRepository;
import com.theclub.eventservice.repository.RegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** The club owner's numbers: registrations, check-ins, attendance and money, per event and for the whole club. */
@Service
public class EventStatsService {

    static final int RECENT_EVENTS = 20;

    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final PaymentRepository payments;
    private final ClubAccess clubAccess;
    private final EventService eventService;
    private final Clock clock;

    public EventStatsService(EventRepository events, RegistrationRepository registrations, PaymentRepository payments,
                             ClubAccess clubAccess, EventService eventService, Clock clock) {
        this.events = events;
        this.registrations = registrations;
        this.payments = payments;
        this.clubAccess = clubAccess;
        this.eventService = eventService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public EventStats.Event event(UUID eventId, AuthenticatedUser caller) {
        Event event = eventService.find(eventId);
        clubAccess.requireOwnerOrAdmin(event.getClubId(), caller);
        return stats(List.of(event)).getFirst();
    }

    @Transactional(readOnly = true)
    public EventStats.Club club(UUID clubId, AuthenticatedUser caller) {
        clubAccess.requireOwnerOrAdmin(clubId, caller);
        List<Event> all = events.findByClubIdOrderByStartTimeDesc(clubId);
        List<EventStats.Event> rows = stats(all);
        LocalDateTime now = LocalDateTime.now(clock);

        Map<EventStatus, Long> byStatus = new EnumMap<>(EventStatus.class);
        long upcoming = 0;
        long registered = 0;
        long checkedIn = 0;
        long startedRegistered = 0;
        long startedCheckedIn = 0;
        Map<String, BigDecimal> revenue = new TreeMap<>();
        for (int i = 0; i < all.size(); i++) {
            Event e = all.get(i);
            EventStats.Event row = rows.get(i);
            byStatus.merge(e.getStatus(), 1L, Long::sum);
            if (e.getStatus() == EventStatus.SCHEDULED && now.isBefore(e.getStartTime())) {
                upcoming++;
            }
            registered += row.registered();
            checkedIn += row.checkedIn();
            if (started(e, now)) {
                startedRegistered += row.registered();
                startedCheckedIn += row.checkedIn();
            }
            row.revenue().forEach((currency, amount) -> revenue.merge(currency, amount, BigDecimal::add));
        }
        return new EventStats.Club(clubId, byStatus.getOrDefault(EventStatus.DRAFT, 0L), upcoming,
                byStatus.getOrDefault(EventStatus.COMPLETED, 0L), byStatus.getOrDefault(EventStatus.CANCELLED, 0L),
                registered, checkedIn, rate(startedCheckedIn, startedRegistered), revenue,
                rows.subList(0, Math.min(rows.size(), RECENT_EVENTS)));
    }

    private List<EventStats.Event> stats(List<Event> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = list.stream().map(Event::getId).toList();
        Map<UUID, Map<RegistrationStatus, Long>> counts = new HashMap<>();
        for (Object[] row : registrations.countByEventAndStatus(ids)) {
            counts.computeIfAbsent((UUID) row[0], id -> new EnumMap<>(RegistrationStatus.class))
                    .put((RegistrationStatus) row[1], ((Number) row[2]).longValue());
        }
        Map<UUID, Map<String, BigDecimal>> money = new HashMap<>();
        for (Object[] row : payments.revenueByEvent(ids)) {
            money.computeIfAbsent((UUID) row[0], id -> new TreeMap<>()).put((String) row[1], (BigDecimal) row[2]);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return list.stream().map(e -> {
            Map<RegistrationStatus, Long> c = counts.getOrDefault(e.getId(), Map.of());
            long checkedIn = c.getOrDefault(RegistrationStatus.CHECKED_IN, 0L);
            long registered = c.getOrDefault(RegistrationStatus.CONFIRMED, 0L) + checkedIn;
            return new EventStats.Event(e.getId(), e.getTitle(), e.getStartTime(), e.getStatus(), e.getCapacity(),
                    registered, checkedIn, c.getOrDefault(RegistrationStatus.PENDING_PAYMENT, 0L),
                    c.getOrDefault(RegistrationStatus.CANCELLED, 0L), money.getOrDefault(e.getId(), Map.of()),
                    started(e, now) ? rate(checkedIn, registered) : null);
        }).toList();
    }

    private static boolean started(Event e, LocalDateTime now) {
        return e.getStatus() == EventStatus.COMPLETED
                || (e.getStatus() == EventStatus.SCHEDULED && !now.isBefore(e.getStartTime()));
    }

    private static Double rate(long checkedIn, long registered) {
        return registered == 0 ? null : Math.round(1000.0 * checkedIn / registered) / 1000.0;
    }
}
