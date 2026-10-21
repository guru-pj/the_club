package com.theclub.userservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update: null fields are left unchanged.
 *
 * @param latitude        with longitude, the user's approximate location (send both, or neither)
 * @param clearLocation   true removes the stored location
 * @param profileComplete true marks the profile setup as finished (or skipped)
 */
public record UpdateUserRequest(
        @Size(min = 1, max = 100) String name,
        @Size(max = 255) String bio,
        @Size(max = 100) String city,
        @Size(max = 500) @Pattern(regexp = "^https?://.*", message = "must be an http(s) URL") String profilePicUrl,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        Boolean clearLocation,
        Boolean profileComplete) {
}
