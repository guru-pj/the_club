package com.theclub.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * RS256 token configuration. Key values are inline PEM or a {@code file:}/{@code classpath:} location.
 *
 * @param privateKey         PKCS#8 PEM; only auth-service has it (it is the only service that issues tokens).
 * @param publicKey          X.509 SubjectPublicKeyInfo PEM used by every service to verify tokens.
 * @param keyId              written to the JWT header as {@code kid}.
 * @param previousPublicKey  optional; still accepted for verification while rotating keys.
 * @param expiration         access token lifetime.
 * @param refreshExpiration  refresh token lifetime (used by auth-service only).
 * @param issuer             value of the "iss" claim.
 */
@ConfigurationProperties(prefix = "theclub.jwt")
public record JwtProperties(String privateKey, String publicKey, String keyId, String previousPublicKey,
                            Duration expiration, Duration refreshExpiration, String issuer) {

    public JwtProperties {
        if (keyId == null || keyId.isBlank()) {
            keyId = "k1";
        }
        if (expiration == null) {
            expiration = Duration.ofMinutes(15);
        }
        if (refreshExpiration == null) {
            refreshExpiration = Duration.ofDays(14);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "the-club";
        }
    }
}
