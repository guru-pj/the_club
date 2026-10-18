package com.theclub.gatewayservice.ratelimit;

/**
 * Classic token bucket: holds up to {@code capacity} tokens, refilled continuously at capacity / period.
 */
final class TokenBucket {

    private final double capacity;
    private final double tokensPerNano;
    private double tokens;
    private long lastRefillNanos;
    private volatile long lastUsedNanos;

    TokenBucket(int capacity, long periodNanos, long nowNanos) {
        this.capacity = capacity;
        this.tokensPerNano = capacity / (double) periodNanos;
        this.tokens = capacity;
        this.lastRefillNanos = nowNanos;
        this.lastUsedNanos = nowNanos;
    }

    /**
     * @return 0 when a token was taken, otherwise the nanoseconds until one becomes available.
     */
    synchronized long tryConsume(long nowNanos) {
        lastUsedNanos = nowNanos;
        long elapsed = nowNanos - lastRefillNanos;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * tokensPerNano);
            lastRefillNanos = nowNanos;
        }
        if (tokens >= 1) {
            tokens -= 1;
            return 0;
        }
        return (long) Math.ceil((1 - tokens) / tokensPerNano);
    }

    long lastUsedNanos() {
        return lastUsedNanos;
    }
}
