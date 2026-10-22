package com.theclub.gatewayservice.ratelimit;

import reactor.core.publisher.Mono;

/** One rate-limit bucket per client key. */
public interface ClientLimiter {

    /** Takes a token for {@code key}: 0 when allowed, otherwise the nanoseconds until the client may retry. */
    Mono<Long> tryAcquire(String key);
}
