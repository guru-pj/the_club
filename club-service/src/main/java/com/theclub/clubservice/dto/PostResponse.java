package com.theclub.clubservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theclub.clubservice.model.Post;
import com.theclub.clubservice.model.PostType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A feed post with its author, reactions and, for a reshare, what was shared.
 *
 * @param likedByMe whether the caller liked it
 */
public record PostResponse(UUID id, UUID clubId, UUID authorId, String authorName, String authorPhotoUrl,
                           PostType type, String title, String content,
                           List<String> photoUrls, UUID eventId,
                           @JsonInclude(JsonInclude.Include.NON_NULL) LinkedActivity activity,
                           @JsonInclude(JsonInclude.Include.NON_NULL) Shared shared,
                           long likeCount, long commentCount, boolean likedByMe,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {

    public record LinkedActivity(UUID id, String title, String type, Double distanceKm, Integer durationSeconds,
                                 LocalDateTime startTime) {
    }

    /**
     * What a reshare points at.
     *
     * @param kind      POST or EVENT
     * @param available false once the original post was deleted (only its ID is left)
     * @param clubId    the club the post or event belongs to
     */
    public record Shared(String kind, boolean available, UUID postId, UUID eventId, UUID clubId, String clubName,
                         String clubLogoUrl, String title, String excerpt, String photoUrl, LocalDateTime startTime,
                         LocalDateTime createdAt) {
    }

    /** Author, counts and shared content, filled in by {@code PostViews}. */
    public record Extras(String authorName, String authorPhotoUrl, long likeCount, long commentCount, boolean likedByMe,
                         Shared shared) {

        public static final Extras NONE = new Extras(null, null, 0, 0, false, null);
    }

    public static PostResponse of(Post post) {
        return of(post, Extras.NONE);
    }

    public static PostResponse of(Post post, Extras extras) {
        LinkedActivity activity = post.getActivityId() == null ? null : new LinkedActivity(post.getActivityId(),
                post.getActivityTitle(), post.getActivityType(), post.getActivityDistanceKm(),
                post.getActivityDurationSeconds(), post.getActivityStartTime());
        return new PostResponse(post.getId(), post.getClubId(), post.getAuthorId(), extras.authorName(),
                extras.authorPhotoUrl(), post.getType(), post.getTitle(), post.getContent(),
                List.copyOf(post.getPhotoUrls()), post.getEventId(), activity, extras.shared(), extras.likeCount(),
                extras.commentCount(), extras.likedByMe(), post.getCreatedAt(), post.getUpdatedAt());
    }
}
