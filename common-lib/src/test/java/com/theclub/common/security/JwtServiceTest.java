package com.theclub.common.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long!!";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final JwtProperties properties = new JwtProperties(SECRET, Duration.ofMinutes(15), null, null);

    @Test
    void roundTripsClaims() {
        JwtService service = new JwtService(properties, Clock.fixed(NOW, ZoneOffset.UTC));
        UUID id = UUID.randomUUID();

        String token = service.issueAccessToken(id, "a@b.com", "Asha", List.of(Roles.USER, Roles.CLUB_OWNER));
        AuthenticatedUser user = service.parse(token);

        assertThat(user.id()).isEqualTo(id);
        assertThat(user.email()).isEqualTo("a@b.com");
        assertThat(user.name()).isEqualTo("Asha");
        assertThat(user.roles()).containsExactlyInAnyOrder(Roles.USER, Roles.CLUB_OWNER);
    }

    @Test
    void rejectsExpiredToken() {
        String token = new JwtService(properties, Clock.fixed(NOW, ZoneOffset.UTC))
                .issueAccessToken(UUID.randomUUID(), "a@b.com", "A", List.of(Roles.USER));
        JwtService later = new JwtService(properties, Clock.fixed(NOW.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.parse(token)).isInstanceOf(JwtService.InvalidTokenException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtProperties other = new JwtProperties("another-secret-that-is-also-32-bytes-long", null, null, null);
        String token = new JwtService(other, Clock.systemUTC())
                .issueAccessToken(UUID.randomUUID(), "a@b.com", "A", List.of(Roles.USER));

        assertThatThrownBy(() -> new JwtService(properties, Clock.systemUTC()).parse(token))
                .isInstanceOf(JwtService.InvalidTokenException.class);
    }

    @Test
    void refusesShortSecret() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("short", null, null, null), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }
}
