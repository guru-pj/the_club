package com.theclub.eventservice.service;

import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.web.ApiException;
import com.theclub.eventservice.client.ClubClient;
import com.theclub.eventservice.client.ClubClient.ClubInfo;
import feign.FeignException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Club ownership and membership checks backed by club-service.
 */
@Component
public class ClubAccess {

    private final ClubClient clubClient;

    public ClubAccess(ClubClient clubClient) {
        this.clubClient = clubClient;
    }

    public ClubInfo getClub(UUID clubId) {
        try {
            return clubClient.getClub(clubId);
        } catch (FeignException.NotFound e) {
            throw ApiException.notFound("Club not found");
        }
    }

    public ClubInfo requireOwnerOrAdmin(UUID clubId, AuthenticatedUser caller) {
        ClubInfo club = getClub(clubId);
        if (!caller.isAdmin() && !caller.id().equals(club.ownerId())) {
            throw ApiException.forbidden("Only the club owner can manage this club's events");
        }
        return club;
    }

    public boolean isOwnerOrAdmin(UUID clubId, AuthenticatedUser caller) {
        return caller.isAdmin() || caller.id().equals(getClub(clubId).ownerId());
    }

    public boolean isMember(UUID clubId, UUID userId) {
        try {
            return clubClient.membership(clubId, userId).member();
        } catch (FeignException.NotFound e) {
            throw ApiException.notFound("Club not found");
        }
    }
}
