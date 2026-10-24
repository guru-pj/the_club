package com.theclub.gatewayservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Edge protections applied by the gateway before requests reach any service.
 *
 * @param trustForwardedFor take the client IP from X-Real-IP / X-Forwarded-For. Enable only when a trusted
 *                          reverse proxy (nginx in the web-app container) sits in front and sets those headers.
 * @param maxBodyBytes      largest accepted request body; larger requests get 413.
 * @param maxUploadBodyBytes largest accepted multipart/form-data body (image and document uploads). The services
 *                          cap each file themselves (MEDIA_MAX_SIZE); this only has to let such a request through.
 * @param rateLimit         per-client-IP token buckets.
 */
@ConfigurationProperties(prefix = "theclub.gateway")
public record GatewayHardeningProperties(boolean trustForwardedFor, Long maxBodyBytes, Long maxUploadBodyBytes,
                                         RateLimit rateLimit) {

    public GatewayHardeningProperties {
        if (maxBodyBytes == null) {
            maxBodyBytes = 1024L * 1024L;
        }
        if (maxUploadBodyBytes == null) {
            maxUploadBodyBytes = 11L * 1024L * 1024L;
        }
        if (rateLimit == null) {
            rateLimit = new RateLimit(null, null, null, null, null, null, null);
        }
    }

    /**
     * @param enabled          master switch.
     * @param strict           bucket for credential and account-recovery endpoints (POST only): brute force,
     *                         credential stuffing and email flooding.
     * @param general          bucket for every /api/** request.
     * @param strictPaths      path patterns covered by the strict bucket.
     * @param maxTrackedClients upper bound on remembered client IPs (memory guard).
     * @param idleEviction     buckets unused for this long are dropped.
     * @param store            memory: each gateway replica counts on its own (the limit multiplies with the replica
     *                         count); redis: one count shared by every replica (needs spring.data.redis.*).
     */
    public record RateLimit(Boolean enabled, Bucket strict, Bucket general, List<String> strictPaths,
                            Integer maxTrackedClients, Duration idleEviction, String store) {

        public static final String MEMORY = "memory";
        public static final String REDIS = "redis";


        public RateLimit {
            if (enabled == null) {
                enabled = true;
            }
            if (strict == null) {
                strict = new Bucket(10, Duration.ofMinutes(1));
            }
            if (general == null) {
                general = new Bucket(300, Duration.ofMinutes(1));
            }
            if (strictPaths == null || strictPaths.isEmpty()) {
                // Token refresh is deliberately not here: the web app calls it on every page load (many users can share
                // one NAT address) and a 256-bit refresh token cannot be guessed; it stays under the general bucket.
                strictPaths = List.of("/api/v1/auth/login", "/api/v1/users/register",
                        "/api/v1/users/password-reset/**", "/api/v1/users/verify-email/**");
            }
            if (maxTrackedClients == null) {
                maxTrackedClients = 100_000;
            }
            if (idleEviction == null) {
                idleEviction = Duration.ofMinutes(10);
            }
            store = store == null || store.isBlank() ? MEMORY : store.trim().toLowerCase(java.util.Locale.ROOT);
            if (!store.equals(MEMORY) && !store.equals(REDIS)) {
                throw new IllegalArgumentException("theclub.gateway.rate-limit.store must be memory or redis, got " + store);
            }
        }
    }

    /**
     * @param capacity requests allowed per period (also the burst size).
     * @param period   time to refill a full bucket.
     */
    public record Bucket(int capacity, Duration period) {

        public Bucket {
            if (capacity < 1) {
                throw new IllegalArgumentException("rate limit capacity must be >= 1");
            }
            if (period == null || period.isZero() || period.isNegative()) {
                throw new IllegalArgumentException("rate limit period must be positive");
            }
        }
    }
}
