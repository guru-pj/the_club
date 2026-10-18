package com.theclub.activityservice.strava;

import com.theclub.common.security.JwtProperties;
import com.theclub.common.web.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StravaStateSignerTest {

    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");
    private static final JwtProperties JWT = new JwtProperties("test-secret-that-is-at-least-32-bytes-long!!", null, null, null);

    private final StravaStateSigner signer = new StravaStateSigner(JWT, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void roundTripsUserId() {
        UUID userId = UUID.randomUUID();
        assertThat(signer.verify(signer.sign(userId))).isEqualTo(userId);
    }

    @Test
    void rejectsTamperedPayload() {
        String state = signer.sign(UUID.randomUUID());
        String forged = new StravaStateSigner(JWT, Clock.fixed(NOW, ZoneOffset.UTC)).sign(UUID.randomUUID());
        String spliced = forged.substring(0, forged.indexOf('.')) + state.substring(state.indexOf('.'));

        assertThatThrownBy(() -> signer.verify(spliced)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsStateSignedWithAnotherKey() {
        JwtProperties other = new JwtProperties("another-secret-that-is-also-32-bytes-long", null, null, null);
        String state = new StravaStateSigner(other, Clock.fixed(NOW, ZoneOffset.UTC)).sign(UUID.randomUUID());

        assertThatThrownBy(() -> signer.verify(state)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsExpiredState() {
        String state = signer.sign(UUID.randomUUID());
        StravaStateSigner later = new StravaStateSigner(JWT,
                Clock.fixed(NOW.plus(StravaStateSigner.VALIDITY).plus(Duration.ofSeconds(1)), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.verify(state)).isInstanceOf(ApiException.class).hasMessageContaining("expired");
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> signer.verify(null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> signer.verify("no-dot")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> signer.verify("abc.%%%")).isInstanceOf(ApiException.class);
    }
}
