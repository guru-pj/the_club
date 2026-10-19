package com.theclub.gatewayservice;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Routes every service to a stub HTTP server that echoes the path and headers it received.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRoutingTest {

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
    void routesPublicApisAndForwardsAuthorization() {
        for (String path : new String[]{"/api/v1/users/me", "/api/v1/auth/login", "/api/v1/activities/me",
                "/api/v1/strava/connect", "/api/v1/clubs", "/api/v1/owner-verification/requests/me",
                "/api/v1/events/club/x", "/api/v1/payments/x", "/api/v1/participation/me",
                "/api/v1/leaderboards/monthly", "/api/v1/notifications/me"}) {
            client.get().uri(path).header("Authorization", "Bearer abc").exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class).isEqualTo("path=" + path + ";auth=Bearer abc;internal=null;bytes=0");
        }
    }

    @Test
    void stripsClientSuppliedInternalKey() {
        client.get().uri("/api/v1/clubs").header("X-Internal-Key", "guess").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body).contains("internal=null"));
    }

    @Test
    void blocksInternalAndUnknownPaths() {
        client.get().uri("/internal/users/by-email/a@b.com").exchange().expectStatus().isNotFound();
        client.get().uri("/api/v1/users/internal/x").exchange().expectStatus().isNotFound();
        client.get().uri("/api/v1/users/%2e%2e/%2e%2e/internal/users/x").exchange().expectStatus().isNotFound();
        client.get().uri("/api/v1/unknown").exchange().expectStatus().isNotFound();
    }

    @Test
    void addsSecurityHeadersAndKeepsRefreshCookie() {
        client.post().uri("/api/v1/auth/login").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("Referrer-Policy", "no-referrer")
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectHeader().value("Set-Cookie", cookie -> assertThat(cookie).startsWith("theclub_refresh=abc").contains("HttpOnly"));

        client.get().uri("/api/v1/clubs").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().doesNotExist("Set-Cookie");
    }
}
