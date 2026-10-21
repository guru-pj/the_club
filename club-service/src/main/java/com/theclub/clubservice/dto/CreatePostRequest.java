package com.theclub.clubservice.dto;

import com.theclub.clubservice.model.PostType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * @param type       the owner's default is ANNOUNCEMENT (EVENT_RECAP requires an eventId of this club); subscribers
 *                   always write a POST.
 * @param activityId optional Strava activity of the author to attach.
 */
public record CreatePostRequest(
        PostType type,
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 5000) String content,
        @Size(max = 10) List<@NotBlank @Size(max = 500) @Pattern(regexp = PostPhotos.URL_PATTERN, message = PostPhotos.URL_MESSAGE) String> photoUrls,
        UUID eventId,
        UUID activityId) {
}
