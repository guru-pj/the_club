package com.theclub.clubservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Partial update: null fields are left unchanged; photoUrls replaces the whole list when given.
 */
public record UpdatePostRequest(
        @Size(min = 1, max = 150) String title,
        @Size(min = 1, max = 5000) String content,
        @Size(max = 10) List<@NotBlank @Size(max = 500) @Pattern(regexp = PostPhotos.URL_PATTERN, message = PostPhotos.URL_MESSAGE) String> photoUrls) {
}
