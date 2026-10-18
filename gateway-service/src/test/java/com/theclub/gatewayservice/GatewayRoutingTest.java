package com.theclub.gatewayservice;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Routes every service to a stub HTTP server that echoes the path and headers it received.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRoutingTest {

    private static final HttpServer STUB = startStub();

    @Autowired
    WebTestClient client;

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                String internal = exchange.getRequestHeaders().getFirst("X-Internal-Key");
                byte[] body = ("path=" + exchange.getRequestURI().getPath() + ";auth=" + auth + ";internal=" + internal)
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stop() {
        STUB.stop(0);
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        String uri = "http://localhost:" + STUB.getAddress().getPort();
        for (String service : new String[]{"USER", "AUTH", "ACTIVITY", "CLUB", "EVENT", "NOTIFICATION"}) {
            registry.add(service + "_SERVICE_URI", () -> uri);
        }
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Test
    void routesPublicApisAndForwardsAuthorization() {
        for (String path : new String[]{"/api/v1/users/me", "/api/v1/auth/login", "/api/v1/activities/me",
                "/api/v1/strava/connect", "/api/v1/clubs", "/api/v1/owner-verification/requests/me",
                "/api/v1/events/club/x", "/api/v1/payments/x", "/api/v1/participation/me",
                "/api/v1/leaderboards/monthly", "/api/v1/notifications/me"}) {
            client.get().uri(path).header("Authorization", "Bearer abc").exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class).isEqualTo("path=" + path + ";auth=Bearer abc;internal=null");
        }
    }

    @Test
    void stripsClientSuppliedInternalKey() {
        client.get().uri("/api/v1/clubs").header("X-Internal-Key", "guess").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> org.assertj.core.api.Assertions.assertThat(body).endsWith("internal=null"));
    }

    @Test
    void blocksInternalAndUnknownPaths() {
        client.get().uri("/internal/users/by-email/a@b.com").exchange().expectStatus().isNotFound();
        client.get().uri("/api/v1/users/internal/x").exchange().expectStatus().isNotFound();
        client.get().uri("/api/v1/users/%2e%2e/%2e%2e/internal/users/x").exchange().expectStatus().isNotFound();
        client.get().uri("/api/v1/unknown").exchange().expectStatus().isNotFound();
    }
}
