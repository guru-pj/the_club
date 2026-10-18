package com.theclub.gatewayservice;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Without a trusted proxy, forwarding headers are ignored: rotating them must not bypass the limit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "theclub.gateway.trust-forwarded-for=false",
        "theclub.gateway.rate-limit.strict.capacity=2"})
@AutoConfigureWebTestClient
class UntrustedForwardedForTest {

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

    @Test
    void ignoresSpoofedClientIpHeaders() {
        for (int i = 0; i < 2; i++) {
            client.post().uri("/api/v1/auth/login").header("X-Real-IP", "1.1.1." + i)
                    .header("X-Forwarded-For", "2.2.2." + i)
                    .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isOk();
        }
        client.post().uri("/api/v1/auth/login").header("X-Real-IP", "1.1.1.9").header("X-Forwarded-For", "2.2.2.9")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isEqualTo(429);
    }
}
