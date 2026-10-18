package com.theclub.clubservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UploadProofRequest(
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 500) @Pattern(regexp = "^https?://.*", message = "must be an http(s) URL") String> proofFiles,
        @Size(max = 2000) String note) {
}
