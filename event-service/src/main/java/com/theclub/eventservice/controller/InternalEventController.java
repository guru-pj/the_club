package com.theclub.eventservice.controller;

import com.theclub.eventservice.dto.InternalEventResponse;
import com.theclub.eventservice.service.EventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service API (requires X-Internal-Key; never routed by the gateway).
 */
@RestController
@RequestMapping("/internal/events")
public class InternalEventController {

    private final EventService eventService;

    public InternalEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/{eventId}")
    public InternalEventResponse get(@PathVariable UUID eventId) {
        return InternalEventResponse.from(eventService.find(eventId));
    }
}
