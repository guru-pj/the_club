package com.theclub.eventservice.service;

import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.security.Roles;
import com.theclub.eventservice.model.Event;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

final class TestData {

    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    /** 2026-10-10T10:00 local time. */
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T04:30:00Z"), ZONE);
    static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private TestData() {
    }

    static AuthenticatedUser user(UUID id, String... roles) {
        return new AuthenticatedUser(id, id + "@example.com", "User", Set.of(roles.length == 0 ? new String[]{Roles.USER} : roles));
    }

    static Event event(UUID clubId, UUID ownerId) {
        Event event = new Event(clubId, ownerId);
        event.setTitle("Sunday Long Run");
        event.setLocation("Marina Beach");
        event.setStartTime(NOW.plusDays(5));
        event.setRegistrationOpensAt(NOW.minusDays(1));
        event.setPrice(BigDecimal.ZERO);
        return event;
    }
}
