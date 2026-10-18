package com.theclub.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param cookieSecure          mark the refresh cookie Secure (disable only for plain-HTTP local development).
 * @param requireVerifiedEmail  refuse logins until the user has verified their email address.
 * @param lockout               brute-force protection per email address.
 */
@ConfigurationProperties(prefix = "theclub.auth")
public record AuthProperties(Boolean cookieSecure, Boolean requireVerifiedEmail, Lockout lockout) {

    public AuthProperties {
        if (cookieSecure == null) {
            cookieSecure = true;
        }
        if (requireVerifiedEmail == null) {
            requireVerifiedEmail = true;
        }
        if (lockout == null) {
            lockout = new Lockout(null, null, null);
        }
    }

    /**
     * @param maxAttempts failed logins allowed within {@code window} before locking.
     * @param window      period in which failures are counted.
     * @param duration    how long login stays locked.
     */
    public record Lockout(Integer maxAttempts, Duration window, Duration duration) {

        public Lockout {
            if (maxAttempts == null) {
                maxAttempts = 5;
            }
            if (window == null) {
                window = Duration.ofMinutes(15);
            }
            if (duration == null) {
                duration = Duration.ofMinutes(15);
            }
        }
    }
}
