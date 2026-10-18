package com.theclub.clubservice.dto;

import jakarta.validation.Valid;

/**
 * @param clubPayload optional overrides; missing values default to the application's club details.
 */
public record ApproveVerificationRequest(boolean createClub, @Valid UpdateClubRequest clubPayload) {
}
