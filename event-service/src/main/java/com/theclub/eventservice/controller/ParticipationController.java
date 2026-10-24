package com.theclub.eventservice.controller;

import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import com.theclub.eventservice.dto.MemberParticipation;
import com.theclub.eventservice.dto.ParticipationResponse;
import com.theclub.eventservice.service.ClubAccess;
import com.theclub.eventservice.service.ParticipationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/participation")
public class ParticipationController {

    private final ParticipationService participationService;
    private final ClubAccess clubAccess;

    public ParticipationController(ParticipationService participationService, ClubAccess clubAccess) {
        this.participationService = participationService;
        this.clubAccess = clubAccess;
    }

    @GetMapping("/me")
    public ParticipationResponse me(@RequestParam(required = false) UUID clubId,
                                    @AuthenticationPrincipal AuthenticatedUser caller) {
        return participationService.forUser(caller.id(), clubId);
    }

    @GetMapping("/users/{userId}")
    public ParticipationResponse user(@PathVariable UUID userId, @RequestParam(required = false) UUID clubId,
                                      @AuthenticationPrincipal AuthenticatedUser caller) {
        if (!caller.id().equals(userId) && !caller.isAdmin()) {
            throw ApiException.forbidden("You can only view your own participation");
        }
        return participationService.forUser(userId, clubId);
    }

    /** Club analytics: who attended and how often. Club owner or admin only. */
    @GetMapping("/clubs/{clubId}")
    public List<MemberParticipation> club(@PathVariable UUID clubId, @AuthenticationPrincipal AuthenticatedUser caller) {
        clubAccess.requireOwnerOrAdmin(clubId, caller);
        return participationService.forClub(clubId);
    }
}
