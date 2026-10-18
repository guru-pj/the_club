package com.theclub.clubservice.dto;

import com.theclub.clubservice.model.ClubMembership;
import com.theclub.clubservice.model.MembershipRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record MembershipResponse(UUID clubId, UUID userId, String name, MembershipRole role, LocalDateTime joinedAt) {

    public static MembershipResponse of(ClubMembership membership, String name) {
        return new MembershipResponse(membership.getClubId(), membership.getUserId(), name, membership.getRole(),
                membership.getJoinedAt());
    }
}
