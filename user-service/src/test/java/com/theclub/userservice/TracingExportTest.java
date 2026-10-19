package com.theclub.userservice;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * With TRACING_ENABLED and OTEL_EXPORTER_OTLP_ENDPOINT set (as docker-compose.observability.yml and the Helm chart do),
 * spans really reach an OTLP collector. A fake collector stands in for the real one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTracing
@ActiveProfiles("test")
class TracingExportTest {

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
    static void tracing(DynamicPropertyRegistry registry) {
        // The same variables the deployments set; application.yml maps them onto the Spring Boot properties
        registry.add("TRACING_ENABLED", () -> "true");
        registry.add("OTEL_EXPORTER_OTLP_ENDPOINT",
                () -> "http://127.0.0.1:" + COLLECTOR.getAddress().getPort() + "/v1/traces");
    }

    @AfterAll
    static void stopCollector() {
        COLLECTOR.stop(0);
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    SdkTracerProvider tracerProvider;

    @Test
    void exportsRequestSpansToTheConfiguredCollector() throws Exception {
        // An API request: /actuator traffic is deliberately left out of traces (NoiseFilteringObservationPredicate)
        mvc.perform(get("/api/v1/observability-check"));

        CompletableResultCode flushed = tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);

        assertThat(flushed.isSuccess()).isTrue();
        assertThat(RECEIVED_SPAN_BATCHES.get()).isPositive();
    }
}
