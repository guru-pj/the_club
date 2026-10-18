package com.theclub.clubservice.dto;

import com.theclub.clubservice.model.ClubType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Proof files are URLs of documents the client has already uploaded. Storing the files themselves
 * (MinIO/S3 multipart upload) is out of scope for this phase.
 */
public record ApplyVerificationRequest(
        @NotBlank @Size(max = 200) String clubName,
        ClubType clubType,
        @Size(max = 100) String city,
        @Size(max = 10) Map<String, @Size(max = 500) String> socialLinks,
        @Size(max = 20) List<@NotBlank @Size(max = 500) @Pattern(regexp = "^https?://.*", message = "must be an http(s) URL") String> proofFiles,
        @Size(max = 5000) String description,
        @Size(max = 20) @Pattern(regexp = "^[+0-9 ()-]*$", message = "must be a phone number") String contactPhone) {
}
