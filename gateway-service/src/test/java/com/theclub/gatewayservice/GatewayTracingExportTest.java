package com.theclub.gatewayservice;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With TRACING_ENABLED and OTEL_EXPORTER_OTLP_ENDPOINT set, routed requests produce spans that reach the collector.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@AutoConfigureTracing
class GatewayTracingExportTest {

    private static final StubBackend STUB = new StubBackend();
    static final AtomicInteger RECEIVED_SPAN_BATCHES = new AtomicInteger();
    static final HttpServer COLLECTOR = startCollector();

    static HttpServer startCollector() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/traces", exchange -> {
                if (exchange.getRequestBody().readAllBytes().length > 0) {
                    RECEIVED_SPAN_BATCHES.incrementAndGet();
                }
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        STUB.register(registry);
        registry.add("TRACING_ENABLED", () -> "true");
        registry.add("OTEL_EXPORTER_OTLP_ENDPOINT",
                () -> "http://127.0.0.1:" + COLLECTOR.getAddress().getPort() + "/v1/traces");
    }

    @AfterAll
    static void stop() {
        STUB.stop();
        COLLECTOR.stop(0);
    }

    @Autowired
    WebTestClient client;
    @Autowired
    SdkTracerProvider tracerProvider;

    @Test
    void exportsSpansForRoutedRequests() {
        client.get().uri("/api/v1/clubs").exchange().expectStatus().isOk();

        CompletableResultCode flushed = tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);

        assertThat(flushed.isSuccess()).isTrue();
        assertThat(RECEIVED_SPAN_BATCHES.get()).isPositive();
    }
}
