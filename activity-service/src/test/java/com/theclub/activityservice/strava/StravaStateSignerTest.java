package com.theclub.activityservice.strava;

import com.theclub.common.security.SecurityProperties;
import com.theclub.common.web.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StravaStateSignerTest {

    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");
    private static final SecurityProperties JWT = new SecurityProperties(List.of(), "test-internal-key-that-is-long-enough");

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
        SecurityProperties other = new SecurityProperties(List.of(), "another-internal-key-that-is-long");
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
