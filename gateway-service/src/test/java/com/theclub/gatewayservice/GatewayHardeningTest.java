package com.theclub.gatewayservice;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rate limiting, trusted client-IP resolution and body size limits, with small limits so tests run fast.
 * Each test uses its own client IP so buckets don't leak between tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "theclub.gateway.trust-forwarded-for=true",
        "theclub.gateway.max-body-bytes=100",
        "theclub.gateway.rate-limit.strict.capacity=2",
        "theclub.gateway.rate-limit.strict.period=PT1M",
        "theclub.gateway.rate-limit.general.capacity=5",
        "theclub.gateway.rate-limit.general.period=PT1M"})
@AutoConfigureWebTestClient
class GatewayHardeningTest {

    private static final StubBackend STUB = new StubBackend();

    @Autowired
    WebTestClient client;

    @AfterAll
    static void stop() {
        STUB.stop();
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        STUB.register(registry);
    }

    private WebTestClient.ResponseSpec login(String ip) {
        return client.post().uri("/api/v1/auth/login").header("X-Real-IP", ip)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange();
    }

    @Test
    void strictBucketLimitsLoginAttemptsPerClient() {
        login("10.0.0.1").expectStatus().isOk();
        login("10.0.0.1").expectStatus().isOk();
        login("10.0.0.1").expectStatus().isEqualTo(429)
                .expectHeader().exists("Retry-After")
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(String.class).value(body -> assertThat(body).contains("Too Many Requests"));

        // another client is unaffected
        login("10.0.0.2").expectStatus().isOk();
    }

    @Test
    void strictBucketOnlyAppliesToPosts() {
        for (int i = 0; i < 4; i++) {
            client.get().uri("/api/v1/auth/validate").header("X-Real-IP", "10.0.1.1").exchange().expectStatus().isOk();
        }
    }

    @Test
    void strictBucketCoversRecoveryEndpointsButNotRefresh() {
        for (int i = 0; i < 2; i++) {
            client.post().uri("/api/v1/users/password-reset/request").header("X-Real-IP", "10.0.7.1")
                    .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isOk();
        }
        client.post().uri("/api/v1/users/verify-email/resend").header("X-Real-IP", "10.0.7.1")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isEqualTo(429);

        for (int i = 0; i < 4; i++) {
            client.post().uri("/api/v1/auth/refresh").header("X-Real-IP", "10.0.8.1").exchange().expectStatus().isOk();
        }
    }

    @Test
    void generalBucketLimitsAllApiCalls() {
        for (int i = 0; i < 5; i++) {
            client.get().uri("/api/v1/clubs").header("X-Real-IP", "10.0.2.1").exchange().expectStatus().isOk();
        }
        client.get().uri("/api/v1/clubs").header("X-Real-IP", "10.0.2.1").exchange().expectStatus().isEqualTo(429);
    }

    @Test
    void usesRightMostForwardedForEntryWhenTrusted() {
        // the left-most entries are client-controlled; spoofing them must not grant a fresh bucket
        for (int i = 0; i < 2; i++) {
            client.post().uri("/api/v1/users/register").header("X-Forwarded-For", "1.2.3." + i + ", 10.0.3.1")
                    .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isOk();
        }
        client.post().uri("/api/v1/users/register").header("X-Forwarded-For", "9.9.9.9, 10.0.3.1")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isEqualTo(429);
    }

    @Test
    void rejectsOversizedBodies() {
        client.post().uri("/api/v1/clubs").header("X-Real-IP", "10.0.4.1")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("x".repeat(101)).exchange()
                .expectStatus().isEqualTo(413);
        client.post().uri("/api/v1/clubs").header("X-Real-IP", "10.0.4.1")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("x".repeat(100)).exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body).endsWith("bytes=100"));
    }

    @Test
    void rejectsOversizedChunkedBodies() {
        client.post().uri("/api/v1/clubs").header("X-Real-IP", "10.0.5.1")
                .contentType(MediaType.APPLICATION_JSON).body(chunks(3, 50), DataBuffer.class).exchange()
                .expectStatus().isEqualTo(413);
    }

    @Test
    void forwardsChunkedBodiesWithinTheLimit() {
        client.post().uri("/api/v1/clubs").header("X-Real-IP", "10.0.6.1")
                .contentType(MediaType.APPLICATION_JSON).body(chunks(2, 40), DataBuffer.class).exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body).endsWith("bytes=80"));
    }

    private static Flux<DataBuffer> chunks(int count, int size) {
        return Flux.range(0, count).map(i -> DefaultDataBufferFactory.sharedInstance
                .wrap("y".repeat(size).getBytes(StandardCharsets.UTF_8)));
    }
}
