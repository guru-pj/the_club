package com.theclub.authservice.service;

import com.theclub.authservice.client.UserCredentials;
import com.theclub.authservice.client.UserServiceClient;
import com.theclub.authservice.config.AuthProperties;
import com.theclub.authservice.dto.TokenResponse;
import com.theclub.authservice.model.AuthToken;
import com.theclub.authservice.repository.AuthTokenRepository;
import com.theclub.common.security.JwtProperties;
import com.theclub.common.security.JwtService;
import com.theclub.common.web.ApiException;
import feign.FeignException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";
    private static final String INVALID_CREDENTIALS = "Invalid email or password";
    private static final String INVALID_REFRESH = "Invalid or expired refresh token";
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserServiceClient userClient;
    private final AuthTokenRepository tokens;
    private final LoginThrottle throttle;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthProperties authProperties;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserServiceClient userClient, AuthTokenRepository tokens, LoginThrottle throttle,
                       JwtService jwtService, JwtProperties jwtProperties, AuthProperties authProperties,
                       PasswordEncoder passwordEncoder, Clock clock) {
        this.userClient = userClient;
        this.tokens = tokens;
        this.throttle = throttle;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.authProperties = authProperties;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Failed attempts are persisted even though the call fails, hence no rollback on ApiException.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse login(String rawEmail, String password) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        throttle.ensureNotLocked(email);
        UserCredentials user;
        try {
            user = userClient.getByEmail(email);
        } catch (FeignException.NotFound e) {
            user = null;
        }
        if (user == null || !user.active() || user.passwordHash() == null
                || !passwordEncoder.matches(password, user.passwordHash())) {
            throttle.recordFailure(email);
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        throttle.recordSuccess(email);
        if (authProperties.requireVerifiedEmail() && !user.emailVerified()) {
            throw ApiException.forbidden("Please verify your email address before logging in")
                    .withCode(EMAIL_NOT_VERIFIED);
        }
        return issue(user);
    }

    /**
     * Rotates the refresh token. The presented token is consumed even when the refresh fails,
     * so a rejected or expired token cannot be retried.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw ApiException.unauthorized(INVALID_REFRESH);
        }
        AuthToken stored = tokens.findByRefreshTokenHash(hash(refreshToken))
                .orElseThrow(() -> ApiException.unauthorized(INVALID_REFRESH));
        tokens.delete(stored);
        if (!stored.getExpiry().isAfter(LocalDateTime.now(clock))) {
            throw ApiException.unauthorized(INVALID_REFRESH);
        }
        UserCredentials user;
        try {
            user = userClient.getById(stored.getUserId());
        } catch (FeignException.NotFound e) {
            throw ApiException.unauthorized(INVALID_REFRESH);
        }
        if (user == null || !user.active()) {
            throw ApiException.unauthorized(INVALID_REFRESH);
        }
        return issue(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            tokens.deleteByRefreshTokenHash(hash(refreshToken));
        }
    }

    /**
     * Revokes every refresh token of the user ("log out everywhere", password reset/change).
     * Access tokens already issued stay valid until they expire (short-lived by design).
     */
    @Transactional
    public int revokeAllSessions(UUID userId) {
        return tokens.deleteByUserId(userId);
    }

    private TokenResponse issue(UserCredentials user) {
        String accessToken = jwtService.issueAccessToken(user.id(), user.email(), user.name(), user.roles());
        String refreshToken = newRefreshToken();
        LocalDateTime now = LocalDateTime.now(clock);
        tokens.save(new AuthToken(user.id(), hash(refreshToken), now.plus(jwtProperties.refreshExpiration()), now));
        return TokenResponse.bearer(accessToken, refreshToken, jwtService.accessTokenTtlSeconds());
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
