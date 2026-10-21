package com.theclub.clubservice.dto;

import com.theclub.clubservice.model.ClubType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * A claim to run one club. Each club is verified on its own; the club is created from these details when an admin
 * approves. Proof can be links (proofFiles) and uploaded documents ({@code POST /requests/{id}/documents}).
 * A contact email or phone is required.
 */
public record ApplyVerificationRequest(
        @NotBlank @Size(max = 120) String clubName,
        @NotNull ClubType clubType,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 10) Map<String, @Size(max = 500) @Pattern(regexp = "^(https?://.*)?$", message = "must be an http(s) URL") String> socialLinks,
        @Size(max = 20) List<@NotBlank @Size(max = 500) @Pattern(regexp = "^https?://.*", message = "must be an http(s) URL") String> proofFiles,
        @Size(max = 5000) String description,
        @Size(max = 20) @Pattern(regexp = "^[+0-9 ()-]*$", message = "must be a phone number") String contactPhone,
        @Size(max = 255) @Email String contactEmail,
        @Size(max = 255) String address,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Size(max = 500) @Pattern(regexp = "^(https?://.*)?$", message = "must be an http(s) URL") String website) {
}
