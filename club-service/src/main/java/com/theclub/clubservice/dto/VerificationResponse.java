package com.theclub.clubservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theclub.clubservice.model.ClubType;
import com.theclub.clubservice.model.VerificationRequest;
import com.theclub.clubservice.model.VerificationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record VerificationResponse(UUID id, UUID userId, String clubName, ClubType clubType, String city,
                                   Map<String, String> socialLinks, List<String> proofFiles, String description,
                                   String contactPhone, VerificationStatus status, String adminNotes,
                                   UUID reviewedBy, LocalDateTime reviewedAt, LocalDateTime createdAt,
                                   LocalDateTime updatedAt,
                                   @JsonInclude(JsonInclude.Include.NON_NULL) UUID createdClubId) {

    public static VerificationResponse of(VerificationRequest r) {
        return of(r, null);
    }

    public static VerificationResponse of(VerificationRequest r, UUID createdClubId) {
        return new VerificationResponse(r.getId(), r.getUserId(), r.getClubName(), r.getClubType(), r.getCity(),
                Map.copyOf(r.getSocialLinks()), List.copyOf(r.getProofFiles()), r.getDescription(),
                r.getContactPhone(), r.getStatus(), r.getAdminNotes(), r.getReviewedBy(), r.getReviewedAt(),
                r.getCreatedAt(), r.getUpdatedAt(), createdClubId);
    }
}
