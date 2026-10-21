package com.theclub.clubservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.theclub.clubservice.model.ClubType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Partial update: null fields are left unchanged (an empty string clears a text field). Latitude and longitude go
 * together; clearLocation removes them. socialLinks, when given, replaces all links. Only admins can rename a club:
 * its name is the one verified with the club owner application.
 */
public record UpdateClubRequest(
        @Size(min = 1, max = 120) @JsonAlias("clubName") String name,
        @Size(max = 2000) String description,
        @Size(max = 100) String city,
        ClubType clubType,
        @Size(max = 500) @Pattern(regexp = "^https?://.*", message = "must be an http(s) URL") String logoUrl,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Size(max = 255) String address,
        Boolean subscriptionsEnabled,
        @Size(max = 1000) String subscriptionBenefits,
        Boolean clearLocation,
        @Size(max = 255) @Email String contactEmail,
        @Size(max = 30) @Pattern(regexp = "^[+0-9 ()-]*$", message = "must be a phone number") String contactPhone,
        // an empty string clears the website; empty links are dropped
        @Size(max = 500) @Pattern(regexp = "^(https?://.*)?$", message = "must be an http(s) URL") String website,
        @Size(max = 10) Map<@Pattern(regexp = "^[a-z]{2,20}$", message = "must be a lowercase name like instagram") String,
                @Size(max = 500) @Pattern(regexp = "^(https?://.*)?$", message = "must be an http(s) URL") String> socialLinks,
        @Size(max = 5000) String rules) {
}
