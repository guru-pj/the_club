package com.theclub.eventservice.dto;

import com.theclub.eventservice.model.Event;
import com.theclub.eventservice.model.RegistrationState;
import com.theclub.eventservice.model.EventStatus;
import com.theclub.eventservice.model.RegistrationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @param spotsLeft    null for unlimited capacity.
 * @param myRegistration the caller's registration status, or null when not registered.
 * @param registrationState whether registration is possible now, or why not.
 */
public record EventResponse(UUID id, UUID clubId, UUID createdBy, String title, String description, String location,
                            LocalDateTime startTime, LocalDateTime registrationOpensAt,
                            LocalDateTime registrationClosesAt, Integer capacity, BigDecimal price, String currency,
                            int points, EventStatus status, long registeredCount, Long spotsLeft,
                            boolean registrationOpen, RegistrationStatus myRegistration, LocalDateTime createdAt,
                            String imageUrl, String rules, boolean subscribersOnly,
                            RegistrationState registrationState) {

    public static EventResponse of(Event event, long registeredCount, long spotsTaken, LocalDateTime now,
                                   RegistrationStatus myRegistration) {
        Long spotsLeft = event.getCapacity() == null ? null : Math.max(0, event.getCapacity() - spotsTaken);
        return new EventResponse(event.getId(), event.getClubId(), event.getCreatedBy(), event.getTitle(),
                event.getDescription(), event.getLocation(), event.getStartTime(), event.getRegistrationOpensAt(),
                event.effectiveRegistrationClosesAt(), event.getCapacity(), event.getPrice(), event.getCurrency(),
                event.getPoints(), event.getStatus(), registeredCount, spotsLeft, event.isRegistrationOpen(now),
                myRegistration, event.getCreatedAt(), event.getImageUrl(), event.getRules(), event.isSubscribersOnly(),
                event.registrationState(now, spotsLeft));
    }
}
