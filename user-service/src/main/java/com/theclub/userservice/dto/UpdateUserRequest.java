package com.theclub.userservice.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update: null fields are left unchanged.
 */
public record UpdateUserRequest(
        @Size(min = 1, max = 100) String name,
        @Size(max = 255) String bio,
        @Size(max = 100) String city,
        @Size(max = 500) @Pattern(regexp = "^https?://.*", message = "must be an http(s) URL") String profilePicUrl) {
}
