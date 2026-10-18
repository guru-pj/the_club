package com.theclub.clubservice.dto;

import com.theclub.clubservice.model.MembershipRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Shapes of the /internal/clubs API (see docs/SERVICE_CONTRACTS.md).
 */
public final class InternalClubResponses {

    private InternalClubResponses() {
    }

    public record ClubInfo(UUID id, String name, UUID ownerId, long memberCount) {
    }

    public record MemberCheck(boolean member, MembershipRole role) {
    }

    public record ClubSummary(UUID id, String name, String logoUrl) {
    }

    public record IdsRequest(@NotNull @Size(max = 1000) List<UUID> ids) {
    }
}
