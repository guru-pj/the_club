package com.theclub.gatewayservice;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@AutoConfigureMetrics
class GatewayObservabilityTest {

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
    void generatesRequestIdAndForwardsIt() {
        client.get().uri("/api/v1/clubs").exchange()
                .expectStatus().isOk()
                .expectHeader().value("X-Request-Id", id -> assertThat(id).matches("[0-9a-f-]{36}"))
                .expectHeader().value("X-Received-Request-Id", forwarded -> assertThat(forwarded).hasSize(36));
    }

    @Test
    void returnsExactlyOneRequestIdEvenWhenTheServiceEchoesIt() {
        client.get().uri("/api/v1/clubs").header("X-Request-Id", "one-id").exchange()
                .expectHeader().values("X-Request-Id", values -> assertThat(values).containsExactly("one-id"));
    }

    @Test
    void keepsAValidCallerRequestId() {
        client.get().uri("/api/v1/clubs").header("X-Request-Id", "client-abc-123").exchange()
                .expectHeader().valueEquals("X-Request-Id", "client-abc-123")
                .expectHeader().valueEquals("X-Received-Request-Id", "client-abc-123");
    }

    @Test
    void replacesAnUnsafeCallerRequestId() {
        client.get().uri("/api/v1/clubs").header("X-Request-Id", "bad id with spaces <script>").exchange()
                .expectHeader().value("X-Request-Id", id -> assertThat(id).matches("[0-9a-f-]{36}"));
    }

    @Test
    void requestIdIsAddedEvenToRejectedRequests() {
        client.get().uri("/internal/users/x").exchange()
                .expectStatus().isNotFound()
                .expectHeader().exists("X-Request-Id");
    }

    @Test
    void actuatorIsServedLocallyAndNeverRoutedToServices() {
        client.get().uri("/actuator/prometheus").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body).contains("application=\"gateway-service\""));
        client.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
        client.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
        assertThat(STUB.receivedPaths).noneMatch(path -> path.startsWith("/actuator"));
    }
}
