package com.theclub.eventservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;
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

    /** Subscribers and followers of the club. */
    @GetMapping("/{clubId}/audience")
    Set<UUID> audience(@PathVariable("clubId") UUID clubId);

    /** Clubs the user follows and subscribes to. */
    @GetMapping("/users/{userId}/related")
    RelatedClubs related(@PathVariable("userId") UUID userId);

    @GetMapping("/nearby")
    List<NearbyClub> nearby(@RequestParam("lat") double lat, @RequestParam("lng") double lng,
                            @RequestParam("radiusKm") double radiusKm, @RequestParam("limit") int limit);

    @GetMapping("/in-city")
    List<ClubSummary> inCity(@RequestParam("city") String city, @RequestParam("limit") int limit);

    record ClubInfo(UUID id, String name, UUID ownerId, long memberCount) {
    }

    record Membership(boolean member, String role) {
    }

    record ClubSummary(UUID id, String name, String logoUrl) {
    }

    record RelatedClubs(List<UUID> followed, List<UUID> subscribed) {
    }

    record NearbyClub(UUID id, String name, String logoUrl, double distanceKm) {
    }
}
