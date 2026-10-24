package com.theclub.clubservice.controller;

import com.theclub.clubservice.dto.InternalClubResponses.ClubInfo;
import com.theclub.clubservice.dto.InternalClubResponses.ClubSummary;
import com.theclub.clubservice.dto.InternalClubResponses.IdsRequest;
import com.theclub.clubservice.dto.InternalClubResponses.MemberCheck;
import com.theclub.clubservice.dto.InternalClubResponses.NearbyClubInfo;
import com.theclub.clubservice.dto.InternalClubResponses.RelatedClubs;
import com.theclub.clubservice.model.Club;
import com.theclub.clubservice.model.MembershipRole;
import com.theclub.clubservice.service.ClubService;
import com.theclub.clubservice.service.DiscoveryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service-to-service API (requires X-Internal-Key; never routed by the gateway).
 */
@RestController
@RequestMapping("/internal/clubs")
public class InternalClubController {

    private final ClubService clubService;

    private final DiscoveryService discovery;

    public InternalClubController(ClubService clubService, DiscoveryService discovery) {
        this.clubService = clubService;
        this.discovery = discovery;
    }

    @GetMapping("/{clubId}")
    public ClubInfo get(@PathVariable UUID clubId) {
        Club club = clubService.get(clubId);
        return new ClubInfo(club.getId(), club.getName(), club.getOwnerId(), clubService.memberCount(clubId));
    }

    @GetMapping("/{clubId}/members/{userId}")
    public MemberCheck member(@PathVariable UUID clubId, @PathVariable UUID userId) {
        MembershipRole role = clubService.roleOf(clubId, userId);
        return new MemberCheck(role != null, role);
    }

    /** Subscribers and followers, for event announcements. */
    @GetMapping("/{clubId}/audience")
    public Set<UUID> audience(@PathVariable UUID clubId) {
        clubService.get(clubId);
        return clubService.audienceIds(clubId);
    }

    @GetMapping("/users/{userId}/related")
    public RelatedClubs related(@PathVariable UUID userId) {
        return new RelatedClubs(clubService.followedClubIds(userId), clubService.subscribedClubIds(userId));
    }

    @GetMapping("/nearby")
    public List<NearbyClubInfo> nearby(@RequestParam double lat, @RequestParam double lng,
                                       @RequestParam(required = false) Double radiusKm,
                                       @RequestParam(required = false) Integer limit) {
        return discovery.nearbyClubs(lat, lng, radiusKm, limit).stream()
                .map(n -> new NearbyClubInfo(n.club().getId(), n.club().getName(), n.club().getLogoUrl(),
                        Math.round(n.distanceKm() * 10) / 10.0))
                .toList();
    }

    /** Clubs whose city matches (case-insensitive): the event feed's "nearby" when the user has no coordinates. */
    @GetMapping("/in-city")
    public List<ClubSummary> inCity(@RequestParam String city, @RequestParam(defaultValue = "50") int limit) {
        return clubService.inCity(city, limit).stream()
                .map(c -> new ClubSummary(c.getId(), c.getName(), c.getLogoUrl()))
                .toList();
    }

    @PostMapping("/summaries")
    public List<ClubSummary> summaries(@Valid @RequestBody IdsRequest request) {
        return clubService.getAll(request.ids()).stream()
                .map(c -> new ClubSummary(c.getId(), c.getName(), c.getLogoUrl()))
                .toList();
    }
}
