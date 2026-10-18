package com.theclub.clubservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "activity-service", contextId = "activityClient", url = "${theclub.clients.activity-service.url:}",
        path = "/internal/activities")
public interface ActivityClient {

    @PostMapping("/stats")
    List<ActivityStats> stats(@RequestBody StatsRequest request);

    @GetMapping("/{id}")
    ActivityDetails get(@PathVariable("id") UUID id);

    record StatsRequest(List<UUID> userIds, LocalDateTime from, LocalDateTime to) {
    }

    record ActivityStats(UUID userId, double totalDistanceKm, long totalDurationSeconds, long sessions) {
    }

    record ActivityDetails(UUID id, UUID userId, String title, String type, Double distance, Integer duration,
                           Integer calories, LocalDateTime startTime, LocalDateTime endTime, String source,
                           String externalId) {
    }
}
