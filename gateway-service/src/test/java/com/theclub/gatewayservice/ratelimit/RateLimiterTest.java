package com.theclub.gatewayservice.ratelimit;

import com.theclub.gatewayservice.config.GatewayHardeningProperties.Bucket;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000_000L);

    private RateLimiter limiter(int capacity, int maxKeys) {
        return new RateLimiter(new Bucket(capacity, Duration.ofMinutes(1)), maxKeys,
                Duration.ofMinutes(10).toNanos(), now::get);
    }

    @Test
    void allowsBurstThenRefillsGradually() {
        RateLimiter limiter = limiter(3, 100);
        assertThat(limiter.tryAcquire("a")).isZero();
        assertThat(limiter.tryAcquire("a")).isZero();
        assertThat(limiter.tryAcquire("a")).isZero();
        long wait = limiter.tryAcquire("a");
        assertThat(wait).isPositive().isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(20));

        now.addAndGet(TimeUnit.SECONDS.toNanos(20)); // 3 per minute -> one token every 20s
        assertThat(limiter.tryAcquire("a")).isZero();
        assertThat(limiter.tryAcquire("a")).isPositive();
    }

    @Test
    void keysAreIndependent() {
        RateLimiter limiter = limiter(1, 100);
        assertThat(limiter.tryAcquire("a")).isZero();
        assertThat(limiter.tryAcquire("a")).isPositive();
        assertThat(limiter.tryAcquire("b")).isZero();
    }

    @Test
    void evictsIdleClientsAndStaysBounded() {
        RateLimiter limiter = limiter(5, 10);
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("ip-" + i);
        }
        now.addAndGet(Duration.ofMinutes(11).toNanos());
        limiter.tryAcquire("ip-new-1"); // map is full -> the 10 idle buckets are evicted first
        assertThat(limiter.trackedKeys()).isEqualTo(1);

        for (int i = 0; i < 50; i++) {
            limiter.tryAcquire("flood-" + i); // all active: map is reset instead of growing
        }
        assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(10);
    }
}
