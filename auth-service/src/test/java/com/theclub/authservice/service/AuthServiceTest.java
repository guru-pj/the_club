package com.theclub.authservice.service;

import com.theclub.authservice.client.UserCredentials;
import com.theclub.authservice.client.UserServiceClient;
import com.theclub.authservice.dto.TokenResponse;
import com.theclub.authservice.model.AuthToken;
import com.theclub.authservice.repository.AuthTokenRepository;
import com.theclub.common.security.AuthenticatedUser;
import com.theclub.common.security.JwtProperties;
import com.theclub.common.security.JwtService;
import com.theclub.common.web.ApiException;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
    private static final String HASH = ENCODER.encode("password123");

    @Mock
    UserServiceClient userClient;
    @Mock
    AuthTokenRepository tokens;

    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    JwtProperties properties = new JwtProperties("test-secret-that-is-at-least-32-bytes-long!!",
            Duration.ofMinutes(15), Duration.ofDays(14), null);
    JwtService jwtService = new JwtService(properties, clock);
    AuthService authService;

    UserCredentials user = new UserCredentials(UUID.randomUUID(), "Asha", "asha@example.com", HASH,
            Set.of("USER"), true);

    @BeforeEach
    void setUp() {
        authService = new AuthService(userClient, tokens, jwtService, properties, ENCODER, clock);
    }

    private static FeignException.NotFound notFound() {
        Request request = Request.create(Request.HttpMethod.GET, "/x", Map.of(), null, StandardCharsets.UTF_8, null);
        return new FeignException.NotFound("not found", request, null, Map.of());
    }

    private static void assertUnauthorized(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void loginIssuesTokensAndStoresHashedRefreshToken() {
        when(userClient.getByEmail("asha@example.com")).thenReturn(user);
        when(tokens.save(any(AuthToken.class))).thenAnswer(inv -> inv.getArgument(0));

        TokenResponse response = authService.login(" Asha@Example.com ", "password123");

        AuthenticatedUser parsed = jwtService.parse(response.accessToken());
        assertThat(parsed.id()).isEqualTo(user.id());
        assertThat(parsed.roles()).containsExactly("USER");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);

        ArgumentCaptor<AuthToken> saved = ArgumentCaptor.forClass(AuthToken.class);
        verify(tokens).save(saved.capture());
        assertThat(saved.getValue().getRefreshTokenHash())
                .hasSize(64)
                .isEqualTo(AuthService.hash(response.refreshToken()))
                .isNotEqualTo(response.refreshToken());
        assertThat(saved.getValue().getExpiry()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(14));
    }

    @Test
    void wrongPasswordIsUnauthorized() {
        when(userClient.getByEmail("asha@example.com")).thenReturn(user);
        assertUnauthorized(() -> authService.login("asha@example.com", "wrong-password"));
        verify(tokens, never()).save(any());
    }

    @Test
    void inactiveUserIsUnauthorized() {
        UserCredentials inactive = new UserCredentials(user.id(), user.name(), user.email(), HASH, user.roles(), false);
        when(userClient.getByEmail("asha@example.com")).thenReturn(inactive);
        assertUnauthorized(() -> authService.login("asha@example.com", "password123"));
    }

    @Test
    void unknownEmailIsUnauthorized() {
        when(userClient.getByEmail("ghost@example.com")).thenThrow(notFound());
        assertUnauthorized(() -> authService.login("ghost@example.com", "password123"));
    }

    @Test
    void refreshRotatesTokenAndPicksUpNewRoles() {
        String oldToken = "old-refresh-token";
        AuthToken stored = new AuthToken(user.id(), AuthService.hash(oldToken),
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(1), LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        UserCredentials promoted = new UserCredentials(user.id(), user.name(), user.email(), HASH,
                Set.of("USER", "CLUB_OWNER"), true);
        when(tokens.findByRefreshTokenHash(AuthService.hash(oldToken))).thenReturn(Optional.of(stored));
        when(userClient.getById(user.id())).thenReturn(promoted);
        when(tokens.save(any(AuthToken.class))).thenAnswer(inv -> inv.getArgument(0));

        TokenResponse response = authService.refresh(oldToken);

        verify(tokens).delete(stored);
        assertThat(response.refreshToken()).isNotEqualTo(oldToken);
        assertThat(jwtService.parse(response.accessToken()).roles()).contains("CLUB_OWNER");
    }

    @Test
    void expiredRefreshTokenIsRejectedAndDeleted() {
        String oldToken = "expired-token";
        AuthToken stored = new AuthToken(user.id(), AuthService.hash(oldToken),
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).minusSeconds(1), LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        when(tokens.findByRefreshTokenHash(AuthService.hash(oldToken))).thenReturn(Optional.of(stored));

        assertUnauthorized(() -> authService.refresh(oldToken));
        verify(tokens).delete(stored);
        verify(userClient, never()).getById(any());
    }

    @Test
    void unknownRefreshTokenIsRejected() {
        when(tokens.findByRefreshTokenHash(any())).thenReturn(Optional.empty());
        assertUnauthorized(() -> authService.refresh("nope"));
    }
}
