package com.theclub.eventservice.controller;

import com.theclub.eventservice.dto.InternalEventResponse;
import com.theclub.eventservice.repository.RegistrationRepository;
import com.theclub.eventservice.service.EventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Service-to-service API (requires X-Internal-Key; never routed by the gateway).
 */
@RestController
@RequestMapping("/internal/events")
public class InternalEventController {

    private final EventService eventService;
    private final RegistrationRepository registrations;

    public InternalEventController(EventService eventService, RegistrationRepository registrations) {
        this.eventService = eventService;
        this.registrations = registrations;
    }

    /** Whether the user checked in at any event of the club (club-service lets attendees review the club). */
    @GetMapping("/attendance")
    public Map<String, Boolean> attendance(@RequestParam UUID clubId, @RequestParam UUID userId) {
        return Map.of("attended", registrations.hasAttendedClub(clubId, userId));
    }

    @GetMapping("/{eventId}")
    public InternalEventResponse get(@PathVariable UUID eventId) {
        return InternalEventResponse.from(eventService.find(eventId));
    }
}
