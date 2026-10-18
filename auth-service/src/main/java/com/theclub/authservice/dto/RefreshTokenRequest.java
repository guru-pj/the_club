package com.theclub.authservice.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional body for refresh/logout; browsers send the token in the HttpOnly cookie instead.
 */
public record RefreshTokenRequest(@Size(max = 512) String refreshToken) {
}
