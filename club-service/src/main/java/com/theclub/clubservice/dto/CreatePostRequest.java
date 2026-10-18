package com.theclub.clubservice.dto;

import com.theclub.clubservice.model.PostType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * @param type       defaults to ANNOUNCEMENT; EVENT_RECAP requires an eventId of this club.
 * @param activityId optional Strava activity of the club owner to attach.
 */
public record CreatePostRequest(
        PostType type,
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 5000) String content,
        @Size(max = 10) List<@NotBlank @Size(max = 500) @Pattern(regexp = "^https?://.*", message = "must be an http(s) URL") String> photoUrls,
        UUID eventId,
        UUID activityId) {
}
