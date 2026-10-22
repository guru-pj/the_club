package com.theclub.gatewayservice.ratelimit;

import com.theclub.gatewayservice.config.GatewayHardeningProperties.Bucket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * In-memory per-key token buckets with bounded memory.
 * <p>
 * State is local to this gateway instance: with several gateway replicas each enforces its own limit. Set
 * {@code theclub.gateway.rate-limit.store=redis} to share one count ({@link RedisClientLimiter}).
 */
public class RateLimiter {

    private static final int CLEANUP_EVERY = 1024;

    private final Bucket config;
    private final int maxKeys;
    private final long idleNanos;
    private final LongSupplier ticker;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong calls = new AtomicLong();

    public RateLimiter(Bucket config, int maxKeys, long idleNanos, LongSupplier ticker) {
        this.config = config;
        this.maxKeys = maxKeys;
        this.idleNanos = idleNanos;
        this.ticker = ticker;
    }

    /**
     * @return 0 if allowed, otherwise nanoseconds until the key may retry.
     */
    public long tryAcquire(String key) {
        long now = ticker.getAsLong();
        if (calls.incrementAndGet() % CLEANUP_EVERY == 0 || buckets.size() >= maxKeys) {
            evict(now);
        }
        TokenBucket bucket = buckets.computeIfAbsent(key,
                k -> new TokenBucket(config.capacity(), config.period().toNanos(), now));
        return bucket.tryConsume(now);
    }

    int trackedKeys() {
        return buckets.size();
    }

    private void evict(long now) {
        buckets.entrySet().removeIf(e -> now - e.getValue().lastUsedNanos() > idleNanos);
        if (buckets.size() >= maxKeys) {
            // Still full of active clients (e.g. a spoofing flood): start over rather than grow without bound.
            buckets.clear();
        }
    }
}
