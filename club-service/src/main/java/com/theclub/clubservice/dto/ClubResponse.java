package com.theclub.clubservice.dto;

import com.theclub.clubservice.model.Club;
import com.theclub.clubservice.model.ClubType;
import com.theclub.clubservice.model.MembershipRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record ClubResponse(UUID id, String name, String description, String city, ClubType clubType, String logoUrl,
                           UUID ownerId, long memberCount, MembershipRole myRole, LocalDateTime createdAt) {

    public static ClubResponse of(Club club, long memberCount, MembershipRole myRole) {
        return new ClubResponse(club.getId(), club.getName(), club.getDescription(), club.getCity(),
                club.getClubType(), club.getLogoUrl(), club.getOwnerId(), memberCount, myRole, club.getCreatedAt());
    }
}
