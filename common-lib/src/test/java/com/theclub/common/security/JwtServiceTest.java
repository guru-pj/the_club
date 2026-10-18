package com.theclub.common.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    static final String PRIVATE = "classpath:jwt-test-private.pem";
    static final String PUBLIC = "classpath:jwt-test-public.pem";
    static final String OTHER_PRIVATE = "classpath:jwt-other-private.pem";
    static final String OTHER_PUBLIC = "classpath:jwt-other-public.pem";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static JwtProperties props(String privateKey, String publicKey, String previous) {
        return new JwtProperties(privateKey, publicKey, "k1", previous, Duration.ofMinutes(15), null, null);
    }

    private static String issue(JwtService service) {
        return service.issueAccessToken(UUID.randomUUID(), "a@b.com", "A", List.of(Roles.USER));
    }

    @Test
    void roundTripsClaimsWithRs256AndKeyId() {
        JwtService service = new JwtService(props(PRIVATE, PUBLIC, null), CLOCK);
        UUID id = UUID.randomUUID();

        String token = service.issueAccessToken(id, "a@b.com", "Asha", List.of(Roles.USER, Roles.CLUB_OWNER));
        AuthenticatedUser user = service.parse(token);

        assertThat(user.id()).isEqualTo(id);
        assertThat(user.email()).isEqualTo("a@b.com");
        assertThat(user.name()).isEqualTo("Asha");
        assertThat(user.roles()).containsExactlyInAnyOrder(Roles.USER, Roles.CLUB_OWNER);
        String header = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[0]));
        assertThat(header).contains("\"alg\":\"RS256\"").contains("\"kid\":\"k1\"");
        assertThat(service.accessTokenTtlSeconds()).isEqualTo(900);
    }

    @Test
    void verifyOnlyServiceParsesButCannotIssue() {
        String token = issue(new JwtService(props(PRIVATE, PUBLIC, null), CLOCK));
        JwtService verifier = new JwtService(props(null, PUBLIC, null), CLOCK);

        assertThat(verifier.parse(token).email()).isEqualTo("a@b.com");
        assertThatThrownBy(() -> issue(verifier)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsExpiredToken() {
        String token = issue(new JwtService(props(PRIVATE, PUBLIC, null), CLOCK));
        JwtService later = new JwtService(props(null, PUBLIC, null),
                Clock.fixed(NOW.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.parse(token)).isInstanceOf(JwtService.InvalidTokenException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String token = issue(new JwtService(props(OTHER_PRIVATE, OTHER_PUBLIC, null), CLOCK));

        assertThatThrownBy(() -> new JwtService(props(null, PUBLIC, null), CLOCK).parse(token))
                .isInstanceOf(JwtService.InvalidTokenException.class);
    }

    @Test
    void acceptsPreviousKeyDuringRotation() {
        String oldToken = issue(new JwtService(props(OTHER_PRIVATE, OTHER_PUBLIC, null), CLOCK));
        JwtService rotated = new JwtService(props(PRIVATE, PUBLIC, OTHER_PUBLIC), CLOCK);

        assertThat(rotated.parse(oldToken).email()).isEqualTo("a@b.com");
        assertThat(rotated.parse(issue(rotated)).email()).isEqualTo("a@b.com");
        assertThat(rotated.verificationKeys()).containsOnlyKeys("k1", "k1-previous");
    }

    @Test
    void rejectsUnsignedAndHmacTokens() {
        JwtService service = new JwtService(props(null, PUBLIC, null), CLOCK);
        String unsigned = Jwts.builder().issuer("the-club").subject(UUID.randomUUID().toString())
                .expiration(Date.from(NOW.plusSeconds(60))).compact();
        String hmac = Jwts.builder().issuer("the-club").subject(UUID.randomUUID().toString())
                .expiration(Date.from(NOW.plusSeconds(60)))
                .signWith(Jwts.SIG.HS256.key().build()).compact();

        assertThatThrownBy(() -> service.parse(unsigned)).isInstanceOf(JwtService.InvalidTokenException.class);
        assertThatThrownBy(() -> service.parse(hmac)).isInstanceOf(JwtService.InvalidTokenException.class);
        assertThatThrownBy(() -> service.parse("garbage")).isInstanceOf(JwtService.InvalidTokenException.class);
    }

    @Test
    void refusesMissingOrMismatchedKeys() {
        assertThatThrownBy(() -> new JwtService(props(null, null, null), CLOCK))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_PUBLIC_KEY");
        assertThatThrownBy(() -> new JwtService(props(null, "not a key", null), CLOCK))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService(props(OTHER_PRIVATE, PUBLIC, null), CLOCK))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("does not match");
    }
}
