package com.theclub.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param secret         HMAC secret shared by every service; must be at least 32 bytes.
 * @param expiration     access token lifetime.
 * @param refreshExpiration refresh token lifetime (used by auth-service only).
 * @param issuer         value of the "iss" claim.
 */
@ConfigurationProperties(prefix = "theclub.jwt")
public record JwtProperties(String secret, Duration expiration, Duration refreshExpiration, String issuer) {

    public JwtProperties {
        if (expiration == null) {
            expiration = Duration.ofHours(1);
        }
        if (refreshExpiration == null) {
            refreshExpiration = Duration.ofDays(14);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "the-club";
        }
    }
}
