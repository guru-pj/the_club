package com.theclub.gatewayservice.ratelimit;

import com.theclub.gatewayservice.config.GatewayHardeningProperties;
import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A token bucket per client in Redis, so every gateway replica shares one count. The refill and the take happen in
 * one Lua script (atomic), timed by Redis' own clock. If Redis is unreachable the request is let through: a missing
 * rate limit is better than the whole site failing.
 */
public class RedisClientLimiter implements ClientLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisClientLimiter.class);
    private static final Duration TIMEOUT = Duration.ofMillis(500);

    /** Returns the milliseconds to wait: 0 when a token was taken. */
    static final RedisScript<Long> TOKEN_BUCKET = RedisScript.of("""
            local capacity = tonumber(ARGV[1])
            local period = tonumber(ARGV[2])
            local t = redis.call('TIME')
            local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            local state = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
            local tokens = tonumber(state[1]) or capacity
            local ts = tonumber(state[2]) or now
            local rate = capacity / period
            tokens = math.min(capacity, tokens + math.max(0, now - ts) * rate)
            local wait = 0
            if tokens >= 1 then
              tokens = tokens - 1
            else
              wait = math.ceil((1 - tokens) / rate)
            end
            redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'ts', tostring(now))
            redis.call('PEXPIRE', KEYS[1], period * 2)
            return wait
            """, Long.class);

    private final ReactiveStringRedisTemplate redis;
    private final String name;
    private final List<String> args;

    public RedisClientLimiter(ReactiveStringRedisTemplate redis, String name, GatewayHardeningProperties.Bucket bucket) {
        this.redis = redis;
        this.name = name;
        this.args = List.of(Integer.toString(bucket.capacity()), Long.toString(bucket.period().toMillis()));
    }

    @Override
    public Mono<Long> tryAcquire(String key) {
        return redis.execute(TOKEN_BUCKET, List.of("theclub:ratelimit:" + name + ":" + key), args)
                .next()
                .timeout(TIMEOUT)
                .map(TimeUnit.MILLISECONDS::toNanos)
                .onErrorResume(e -> {
                    Metrics.counter("theclub.gateway.rate_limit_errors", "bucket", name).increment();
                    log.warn("Rate limit check in Redis failed, letting the request through: {}", e.getMessage());
                    return Mono.just(0L);
                })
                .defaultIfEmpty(0L);
    }
}
