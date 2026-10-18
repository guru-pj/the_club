package com.theclub.authservice.service;

import com.theclub.authservice.config.AuthProperties;
import com.theclub.authservice.model.LoginAttempt;
import com.theclub.authservice.repository.LoginAttemptRepository;
import com.theclub.common.web.ApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Locks login for an email after too many failed attempts within a window. Callers run inside the login
 * transaction, which must not roll back on {@link ApiException} so failures are persisted.
 */
@Component
public class LoginThrottle {

    private final LoginAttemptRepository attempts;
    private final AuthProperties.Lockout policy;
    private final Clock clock;

    public LoginThrottle(LoginAttemptRepository attempts, AuthProperties properties, Clock clock) {
        this.attempts = attempts;
        this.policy = properties.lockout();
        this.clock = clock;
    }

    /**
     * @throws ApiException 429 with Retry-After while the email is locked, even for a correct password.
     */
    public void ensureNotLocked(String email) {
        LocalDateTime now = LocalDateTime.now(clock);
        attempts.findByEmail(email)
                .filter(a -> a.getLockedUntil() != null && now.isBefore(a.getLockedUntil()))
                .ifPresent(a -> {
                    long seconds = Math.max(1, Duration.between(now, a.getLockedUntil()).toSeconds());
                    long minutes = (seconds + 59) / 60;
                    throw ApiException.tooManyRequests("Too many failed login attempts. Try again in " + minutes
                                    + (minutes == 1 ? " minute." : " minutes."))
                            .withHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
                });
    }

    public void recordFailure(String email) {
        LocalDateTime now = LocalDateTime.now(clock);
        LoginAttempt attempt = attempts.findByEmail(email).orElseGet(() -> new LoginAttempt(email));
        if (attempt.getLockedUntil() != null && !now.isBefore(attempt.getLockedUntil())) {
            attempt.setLockedUntil(null);
            attempt.setFailedCount(0);
            attempt.setWindowStartedAt(null);
        }
        if (attempt.getWindowStartedAt() == null || !now.isBefore(attempt.getWindowStartedAt().plus(policy.window()))) {
            attempt.setFailedCount(0);
            attempt.setWindowStartedAt(now);
        }
        attempt.setFailedCount(attempt.getFailedCount() + 1);
        if (attempt.getFailedCount() >= policy.maxAttempts()) {
            attempt.setLockedUntil(now.plus(policy.duration()));
            attempt.setFailedCount(0);
            attempt.setWindowStartedAt(null);
        }
        attempts.save(attempt);
    }

    public void recordSuccess(String email) {
        attempts.deleteByEmail(email);
    }
}
