package com.theclub.clubservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.UUID;

@FeignClient(name = "event-service", contextId = "eventClient", url = "${theclub.clients.event-service.url:}",
        path = "/internal/events")
public interface EventClient {

    @GetMapping("/{eventId}")
    EventSummary get(@PathVariable("eventId") UUID eventId);

    record EventSummary(UUID id, UUID clubId, String title, LocalDateTime startTime, String status) {
    }

    /** Whether the user checked in at any event of the club (they may then review it). */
    @GetMapping("/attendance")
    AttendanceCheck attendance(@RequestParam("clubId") UUID clubId, @RequestParam("userId") UUID userId);

    record AttendanceCheck(boolean attended) {
    }
}
