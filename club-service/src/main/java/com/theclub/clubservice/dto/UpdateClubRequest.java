package com.theclub.clubservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.theclub.clubservice.model.ClubType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update: null fields are left unchanged.
 */
public record UpdateClubRequest(
        @Size(min = 1, max = 120) @JsonAlias("clubName") String name,
        @Size(max = 2000) String description,
        @Size(max = 100) String city,
        ClubType clubType,
        @Size(max = 500) @Pattern(regexp = "^https?://.*", message = "must be an http(s) URL") String logoUrl) {
}
