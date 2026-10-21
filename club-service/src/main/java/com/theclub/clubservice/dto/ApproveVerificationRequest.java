package com.theclub.clubservice.dto;

import jakarta.validation.Valid;

/**
 * Approving always creates the club and links it to the applicant.
 *
 * @param clubPayload optional corrections; its name is the club's official, verified name (defaults to the name
 *                    in the application). Missing values come from the application.
 */
public record ApproveVerificationRequest(@Valid UpdateClubRequest clubPayload) {
}
