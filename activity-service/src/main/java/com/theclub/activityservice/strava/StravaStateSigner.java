package com.theclub.activityservice.strava;

import com.theclub.common.security.JwtProperties;
import com.theclub.common.web.ApiException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * Signs the OAuth "state" parameter so the public callback can trust which user started the connect flow.
 * Format: base64url(userId:expiryEpochSeconds) "." base64url(HMAC-SHA256(payload)).
 */
@Component
public class StravaStateSigner {

    static final Duration VALIDITY = Duration.ofMinutes(10);
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] key;
    private final Clock clock;

    public StravaStateSigner(JwtProperties jwtProperties, Clock clock) {
        this.key = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public String sign(UUID userId) {
        long expiry = clock.instant().plus(VALIDITY).getEpochSecond();
        String payload = base64(userId + ":" + expiry);
        return payload + "." + base64(hmac(payload));
    }

    /**
     * @throws ApiException 403 when the state is malformed, tampered with or expired.
     */
    public UUID verify(String state) {
        if (state == null) {
            throw invalid();
        }
        int dot = state.indexOf('.');
        if (dot <= 0) {
            throw invalid();
        }
        String payload = state.substring(0, dot);
        byte[] signature;
        try {
            signature = Base64.getUrlDecoder().decode(state.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        if (!MessageDigest.isEqual(hmac(payload), signature)) {
            throw invalid();
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8).split(":");
            if (parts.length != 2) {
                throw invalid();
            }
            if (clock.instant().getEpochSecond() > Long.parseLong(parts[1])) {
                throw ApiException.forbidden("Strava authorization state has expired; start again");
            }
            return UUID.fromString(parts[0]);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base64(String value) {
        return base64(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static ApiException invalid() {
        return ApiException.forbidden("Invalid Strava authorization state");
    }
}
