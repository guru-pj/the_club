package com.theclub.eventservice.service;

import com.theclub.common.web.ApiException;
import com.theclub.eventservice.model.Event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Consistency rules for an event's schedule and settings.
 */
final class EventRules {

    static final int MAX_POINTS = 1000;

    private EventRules() {
    }

    static void validate(Event event, LocalDateTime now, boolean requireFutureStart) {
        LocalDateTime start = event.getStartTime();
        LocalDateTime opens = event.getRegistrationOpensAt();
        LocalDateTime closes = event.getRegistrationClosesAt();
        if (start == null || opens == null) {
            throw ApiException.badRequest("startTime and registrationOpensAt are required");
        }
        if (requireFutureStart && !start.isAfter(now)) {
            throw ApiException.badRequest("startTime must be in the future");
        }
        if (!opens.isBefore(start)) {
            throw ApiException.badRequest("registrationOpensAt must be before startTime");
        }
        if (closes != null && (!closes.isAfter(opens) || closes.isAfter(start))) {
            throw ApiException.badRequest("registrationClosesAt must be after registrationOpensAt and no later than startTime");
        }
        if (event.getCapacity() != null && event.getCapacity() < 1) {
            throw ApiException.badRequest("capacity must be at least 1");
        }
        if (event.getPrice() == null || event.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest("price must be zero or positive");
        }
        if (event.getPoints() < 0 || event.getPoints() > MAX_POINTS) {
            throw ApiException.badRequest("points must be between 0 and " + MAX_POINTS);
        }
    }
}
