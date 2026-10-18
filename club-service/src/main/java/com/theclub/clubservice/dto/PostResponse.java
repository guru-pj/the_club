package com.theclub.clubservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theclub.clubservice.model.Post;
import com.theclub.clubservice.model.PostType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PostResponse(UUID id, UUID clubId, UUID authorId, PostType type, String title, String content,
                           List<String> photoUrls, UUID eventId,
                           @JsonInclude(JsonInclude.Include.NON_NULL) LinkedActivity activity,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {

    public record LinkedActivity(UUID id, String title, String type, Double distanceKm, Integer durationSeconds,
                                 LocalDateTime startTime) {
    }

    public static PostResponse of(Post post) {
        LinkedActivity activity = post.getActivityId() == null ? null : new LinkedActivity(post.getActivityId(),
                post.getActivityTitle(), post.getActivityType(), post.getActivityDistanceKm(),
                post.getActivityDurationSeconds(), post.getActivityStartTime());
        return new PostResponse(post.getId(), post.getClubId(), post.getAuthorId(), post.getType(), post.getTitle(),
                post.getContent(), List.copyOf(post.getPhotoUrls()), post.getEventId(), activity, post.getCreatedAt(),
                post.getUpdatedAt());
    }
}
