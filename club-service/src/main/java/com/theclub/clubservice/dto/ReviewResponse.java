package com.theclub.clubservice.dto;

import com.theclub.clubservice.model.ClubReview;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewResponse(UUID id, UUID userId, String userName, int rating, String comment,
                             LocalDateTime createdAt, LocalDateTime updatedAt, String ownerReply,
                             LocalDateTime ownerReplyAt) {

    public static ReviewResponse of(ClubReview review, String userName) {
        return new ReviewResponse(review.getId(), review.getUserId(), userName, review.getRating(),
                review.getComment(), review.getCreatedAt(), review.getUpdatedAt(), review.getOwnerReply(),
                review.getOwnerReplyAt());
    }
}
