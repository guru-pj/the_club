package com.theclub.clubservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.theclub.clubservice.model.ClubType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The owner is always the caller; an "ownerId" in the body is ignored. Latitude and longitude go together.
 */
public record CreateClubRequest(
        @NotBlank @Size(max = 120) @JsonAlias("clubName") String name,
        @Size(max = 2000) String description,
        @Size(max = 100) String city,
        ClubType clubType,
        @Size(max = 500) @Pattern(regexp = "^https?://.*", message = "must be an http(s) URL") String logoUrl,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Size(max = 255) String address,
        Boolean subscriptionsEnabled,
        @Size(max = 1000) String subscriptionBenefits) {
}
