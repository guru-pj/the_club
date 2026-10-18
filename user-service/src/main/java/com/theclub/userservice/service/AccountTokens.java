package com.theclub.userservice.service;

import com.theclub.common.web.ApiException;
import com.theclub.userservice.model.TokenPurpose;
import com.theclub.userservice.model.UserToken;
import com.theclub.userservice.repository.UserTokenRepository;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and redeems emailed single-use tokens. Callers own the transaction.
 */
@Component
public class AccountTokens {

    static final String INVALID = "This link is invalid or has expired";
    /** Minimum gap between emails of the same kind to one user. */
    static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final int TOKEN_BYTES = 32;

    private final UserTokenRepository tokens;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AccountTokens(UserTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    /**
     * Issues a new token, superseding older unused ones of the same purpose.
     *
     * @return the raw token, or empty when one was issued less than {@link #RESEND_INTERVAL} ago.
     */
    public Optional<String> issue(UUID userId, TokenPurpose purpose) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean recent = tokens.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose)
                .map(t -> t.getCreatedAt().isAfter(now.minus(RESEND_INTERVAL)))
                .orElse(false);
        if (recent) {
            return Optional.empty();
        }
        tokens.invalidateUnused(userId, purpose, now);
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new UserToken(userId, purpose, hash(raw), now));
        return Optional.of(raw);
    }

    /**
     * Looks up a usable token without consuming it.
     *
     * @throws ApiException 400 when the token is unknown, of another purpose, used or expired.
     */
    public UserToken find(String rawToken, TokenPurpose purpose) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.badRequest(INVALID);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return tokens.findByTokenHash(hash(rawToken))
                .filter(t -> t.getPurpose() == purpose)
                .filter(t -> t.getUsedAt() == null)
                .filter(t -> t.getExpiresAt().isAfter(now))
                .orElseThrow(() -> ApiException.badRequest(INVALID));
    }

    public void markUsed(UserToken token) {
        token.setUsedAt(LocalDateTime.now(clock));
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
