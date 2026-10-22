package com.theclub.gatewayservice.ratelimit;

import reactor.core.publisher.Mono;

/** Buckets in this gateway replica's memory; with several replicas each counts on its own. */
public class MemoryClientLimiter implements ClientLimiter {

    private final RateLimiter limiter;

    public MemoryClientLimiter(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public Mono<Long> tryAcquire(String key) {
        return Mono.just(limiter.tryAcquire(key));
    }
}
