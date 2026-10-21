package com.theclub.clubservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theclub.clubservice.model.ClubType;
import com.theclub.clubservice.model.VerificationDocument;
import com.theclub.clubservice.model.VerificationRequest;
import com.theclub.clubservice.model.VerificationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record VerificationResponse(UUID id, UUID userId, String clubName, ClubType clubType, String city,
                                   Map<String, String> socialLinks, List<String> proofFiles, String description,
                                   String contactPhone, String contactEmail, String address, Double latitude,
                                   Double longitude, String website, VerificationStatus status, String adminNotes,
                                   UUID reviewedBy, LocalDateTime reviewedAt, LocalDateTime createdAt,
                                   LocalDateTime updatedAt,
                                   @JsonInclude(JsonInclude.Include.NON_NULL) UUID createdClubId,
                                   List<Document> documents) {

    /** An uploaded proof document; download it from {@code /requests/{id}/documents/{documentId}}. */
    public record Document(UUID id, String fileName, String contentType, long sizeBytes, LocalDateTime uploadedAt) {

        public static Document of(VerificationDocument d) {
            return new Document(d.getId(), d.getFileName(), d.getContentType(), d.getSizeBytes(), d.getCreatedAt());
        }
    }

    public static VerificationResponse of(VerificationRequest r) {
        return of(r, List.of());
    }

    public static VerificationResponse of(VerificationRequest r, List<VerificationDocument> documents) {
        return new VerificationResponse(r.getId(), r.getUserId(), r.getClubName(), r.getClubType(), r.getCity(),
                Map.copyOf(r.getSocialLinks()), List.copyOf(r.getProofFiles()), r.getDescription(),
                r.getContactPhone(), r.getContactEmail(), r.getAddress(), r.getLatitude(), r.getLongitude(),
                r.getWebsite(), r.getStatus(), r.getAdminNotes(), r.getReviewedBy(), r.getReviewedAt(),
                r.getCreatedAt(), r.getUpdatedAt(), r.getClubId(), documents.stream().map(Document::of).toList());
    }
}
