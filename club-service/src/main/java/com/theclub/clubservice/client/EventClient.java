package com.theclub.clubservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDateTime;
import java.util.UUID;

@FeignClient(name = "event-service", contextId = "eventClient", url = "${theclub.clients.event-service.url:}",
        path = "/internal/events")
public interface EventClient {

    @GetMapping("/{eventId}")
    EventSummary get(@PathVariable("eventId") UUID eventId);

    record EventSummary(UUID id, UUID clubId, String title, LocalDateTime startTime, String status) {
    }
}
