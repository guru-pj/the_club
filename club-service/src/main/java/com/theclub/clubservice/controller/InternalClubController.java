package com.theclub.clubservice.controller;

import com.theclub.clubservice.dto.InternalClubResponses.ClubInfo;
import com.theclub.clubservice.dto.InternalClubResponses.ClubSummary;
import com.theclub.clubservice.dto.InternalClubResponses.IdsRequest;
import com.theclub.clubservice.dto.InternalClubResponses.MemberCheck;
import com.theclub.clubservice.model.Club;
import com.theclub.clubservice.model.MembershipRole;
import com.theclub.clubservice.service.ClubService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Service-to-service API (requires X-Internal-Key; never routed by the gateway).
 */
@RestController
@RequestMapping("/internal/clubs")
public class InternalClubController {

    private final ClubService clubService;

    public InternalClubController(ClubService clubService) {
        this.clubService = clubService;
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

    @PostMapping("/summaries")
    public List<ClubSummary> summaries(@Valid @RequestBody IdsRequest request) {
        return clubService.getAll(request.ids()).stream()
                .map(c -> new ClubSummary(c.getId(), c.getName(), c.getLogoUrl()))
                .toList();
    }
}
