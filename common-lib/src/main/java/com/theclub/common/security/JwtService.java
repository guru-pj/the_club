package com.theclub.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Issues and validates RS256 access tokens. Only auth-service holds the private key and can issue tokens;
 * every service verifies locally with the public key, so no network hop to auth-service is needed per request.
 */
public class JwtService {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final RSAPublicKey previousPublicKey;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        if (properties.publicKey() == null || properties.publicKey().isBlank()) {
            throw new IllegalStateException("theclub.jwt.public-key (JWT_PUBLIC_KEY) must be configured with the "
                    + "RSA public key that verifies access tokens");
        }
        this.publicKey = RsaKeys.publicKey(properties.publicKey(), "theclub.jwt.public-key");
        this.privateKey = isSet(properties.privateKey())
                ? RsaKeys.privateKey(properties.privateKey(), "theclub.jwt.private-key") : null;
        this.previousPublicKey = isSet(properties.previousPublicKey())
                ? RsaKeys.publicKey(properties.previousPublicKey(), "theclub.jwt.previous-public-key") : null;
        if (privateKey != null && !privateKey.getModulus().equals(publicKey.getModulus())) {
            throw new IllegalStateException("theclub.jwt.private-key does not match theclub.jwt.public-key");
        }
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @throws IllegalStateException when this service has no private key (only auth-service issues tokens).
     */
    public String issueAccessToken(UUID userId, String email, String name, Collection<String> roles) {
        if (privateKey == null) {
            throw new IllegalStateException("This service cannot issue tokens: theclub.jwt.private-key is not configured");
        }
        Instant now = clock.instant();
        return Jwts.builder()
                .header().keyId(properties.keyId()).and()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .claim("email", email)
                .claim("name", name)
                .claim("roles", List.copyOf(roles))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.expiration())))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public long accessTokenTtlSeconds() {
        return properties.expiration().toSeconds();
    }

    /**
     * Verification keys by key id: the current key first, then the previous one during rotation.
     */
    public Map<String, RSAPublicKey> verificationKeys() {
        Map<String, RSAPublicKey> keys = new LinkedHashMap<>();
        keys.put(properties.keyId(), publicKey);
        if (previousPublicKey != null) {
            keys.put(properties.keyId() + "-previous", previousPublicKey);
        }
        return keys;
    }

    /**
     * @throws InvalidTokenException when the token is malformed, expired or not signed by a trusted key.
     */
    public AuthenticatedUser parse(String token) {
        Claims claims = null;
        for (RSAPublicKey key : verificationKeys().values()) {
            try {
                claims = Jwts.parser()
                        .verifyWith(key)
                        .requireIssuer(properties.issuer())
                        .clock(() -> Date.from(clock.instant()))
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
                break;
            } catch (SignatureException e) {
                // signed by another key: try the next trusted key
            } catch (JwtException | IllegalArgumentException e) {
                throw new InvalidTokenException(e.getMessage());
            }
        }
        if (claims == null) {
            throw new InvalidTokenException("Token is not signed by a trusted key");
        }
        try {
            Set<String> roles = new LinkedHashSet<>();
            Object raw = claims.get("roles");
            if (raw instanceof Collection<?> values) {
                values.forEach(v -> roles.add(String.valueOf(v)));
            }
            return new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("name", String.class),
                    roles);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidTokenException("Token has an invalid subject");
        }
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }
}
