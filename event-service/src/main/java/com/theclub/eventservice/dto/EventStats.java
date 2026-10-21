package com.theclub.eventservice.dto;

import com.theclub.eventservice.model.EventStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Attendance and money numbers for the club owner. */
public final class EventStats {

    private EventStats() {
    }

    /**
     * One event.
     *
     * @param registered     confirmed and checked-in registrations
     * @param attendanceRate checked in ÷ registered once the event has started; null before
     * @param revenue        successful payments by currency
     */
    public record Event(UUID eventId, String title, LocalDateTime startTime, EventStatus status, Integer capacity,
                        long registered, long checkedIn, long pendingPayment, long cancelled,
                        Map<String, BigDecimal> revenue, Double attendanceRate) {
    }

    /**
     * All of a club's events.
     *
     * @param upcoming       published events that haven't started
     * @param registrations  confirmed and checked-in registrations across all events
     * @param attendanceRate checked in ÷ registered over events that have started; null before any
     * @param events         the most recent events, newest first
     */
    public record Club(UUID clubId, long drafts, long upcoming, long completed, long cancelled, long registrations,
                       long checkIns, Double attendanceRate, Map<String, BigDecimal> revenue, List<Event> events) {
    }
}
