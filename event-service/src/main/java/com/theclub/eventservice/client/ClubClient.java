package com.theclub.eventservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "club-service", contextId = "clubClient", url = "${theclub.clients.club-service.url:}",
        path = "/internal/clubs")
public interface ClubClient {

    @GetMapping("/{clubId}")
    ClubInfo getClub(@PathVariable("clubId") UUID clubId);

    @GetMapping("/{clubId}/members/{userId}")
    Membership membership(@PathVariable("clubId") UUID clubId, @PathVariable("userId") UUID userId);

    @PostMapping("/summaries")
    List<ClubSummary> summaries(@RequestBody IdsRequest request);

    record ClubInfo(UUID id, String name, UUID ownerId, long memberCount) {
    }

    record Membership(boolean member, String role) {
    }

    record ClubSummary(UUID id, String name, String logoUrl) {
    }
}
