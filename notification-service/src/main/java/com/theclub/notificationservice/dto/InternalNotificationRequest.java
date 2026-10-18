package com.theclub.notificationservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Message and type longer than the column sizes are truncated rather than rejected.
 */
public record InternalNotificationRequest(
        @NotNull UUID userId,
        @NotBlank String type,
        @NotBlank String message,
        @Valid EmailRequest email) {
}
