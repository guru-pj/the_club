package com.theclub.gatewayservice.ratelimit;

import com.theclub.gatewayservice.config.GatewayHardeningProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The shared limiter against a real Redis: replicas share one count, and a Redis outage never blocks requests. */
@Testcontainers(disabledWithoutDocker = true)
class RedisClientLimiterTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    static LettuceConnectionFactory factory;
    static ReactiveStringRedisTemplate redis;
    static final GatewayHardeningProperties.Bucket THREE_PER_MINUTE = new GatewayHardeningProperties.Bucket(3, Duration.ofMinutes(1));

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        factory.start();
        redis = new ReactiveStringRedisTemplate(factory);
    }

    @AfterAll
    static void disconnect() {
        factory.destroy();
    }

    @Test
    void replicasShareOneCount() {
        // two gateway replicas, each with its own limiter, against the same Redis
        RedisClientLimiter first = new RedisClientLimiter(redis, "strict", THREE_PER_MINUTE);
        RedisClientLimiter second = new RedisClientLimiter(redis, "strict", THREE_PER_MINUTE);
        String client = "10.0.0." + UUID.randomUUID().hashCode();

        assertThat(first.tryAcquire(client).block()).isZero();
        assertThat(second.tryAcquire(client).block()).isZero();
        assertThat(first.tryAcquire(client).block()).isZero();
        long wait = second.tryAcquire(client).block();
        // one token comes back every 20 seconds
        assertThat(wait).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(20).toNanos());

        assertThat(first.tryAcquire("another-client-" + client).block()).isZero();
        // the buckets are separate per name
        assertThat(new RedisClientLimiter(redis, "general", THREE_PER_MINUTE).tryAcquire(client).block()).isZero();
        // and expire on their own
        assertThat(redis.getExpire("theclub:ratelimit:strict:" + client).block()).isPositive();
    }

    @Test
    void anUnreachableRedisLetsRequestsThrough() {
        LettuceConnectionFactory nowhere = new LettuceConnectionFactory("127.0.0.1", 1);
        nowhere.afterPropertiesSet();
        nowhere.start();
        try {
            RedisClientLimiter limiter = new RedisClientLimiter(new ReactiveStringRedisTemplate(nowhere), "strict", THREE_PER_MINUTE);
            for (int i = 0; i < 5; i++) {
                assertThat(limiter.tryAcquire("10.9.9.9").block(Duration.ofSeconds(5))).isZero();
            }
        } finally {
            nowhere.destroy();
        }
    }
}
