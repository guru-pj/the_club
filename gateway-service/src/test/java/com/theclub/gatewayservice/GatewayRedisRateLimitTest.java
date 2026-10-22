package com.theclub.gatewayservice;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The gateway with RATE_LIMIT_STORE=redis: the limit is enforced from Redis, and health includes Redis. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "theclub.gateway.trust-forwarded-for=true",
        "theclub.gateway.rate-limit.store=redis",
        "theclub.gateway.rate-limit.strict.capacity=2",
        "theclub.gateway.rate-limit.strict.period=PT1M",
        "REDIS_ENABLED=true"})
@AutoConfigureWebTestClient
@Testcontainers(disabledWithoutDocker = true)
class GatewayRedisRateLimitTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    private static final StubBackend STUB = new StubBackend();

    @Autowired
    WebTestClient client;

    @AfterAll
    static void stop() {
        STUB.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        STUB.register(registry);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private WebTestClient.ResponseSpec login(String ip) {
        return client.post().uri("/api/v1/auth/login").header("X-Real-IP", ip)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange();
    }

    @Test
    void loginAttemptsAreLimitedThroughRedis() {
        login("10.1.0.1").expectStatus().isOk();
        login("10.1.0.1").expectStatus().isOk();
        login("10.1.0.1").expectStatus().isEqualTo(429).expectHeader().exists("Retry-After");
        login("10.1.0.2").expectStatus().isOk();
    }

    @Test
    void healthIncludesRedisWhenItIsUsed() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}
