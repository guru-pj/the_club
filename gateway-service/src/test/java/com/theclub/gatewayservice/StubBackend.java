package com.theclub.gatewayservice;

import com.sun.net.httpserver.HttpServer;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Plain HTTP server standing in for every downstream service. It echoes what it received and, for auth paths,
 * sets a refresh cookie so tests can check the gateway passes Set-Cookie through.
 */
final class StubBackend {

    private final HttpServer server;
    /** Every path the stub received, so tests can assert something was never forwarded. */
    final java.util.List<String> receivedPaths = new java.util.concurrent.CopyOnWriteArrayList<>();

    StubBackend() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", exchange -> {
            byte[] received;
            try (InputStream in = exchange.getRequestBody()) {
                received = in.readAllBytes();
            }
            receivedPaths.add(exchange.getRequestURI().getPath());
            String requestId = exchange.getRequestHeaders().getFirst("X-Request-Id");
            if (requestId != null) {
                exchange.getResponseHeaders().add("X-Received-Request-Id", requestId);
                // like the real services (RequestIdFilter), echo the id back
                exchange.getResponseHeaders().add("X-Request-Id", requestId);
            }
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            String internal = exchange.getRequestHeaders().getFirst("X-Internal-Key");
            byte[] body = ("path=" + exchange.getRequestURI().getPath() + ";auth=" + auth + ";internal=" + internal
                    + ";bytes=" + received.length).getBytes(StandardCharsets.UTF_8);
            if (exchange.getRequestURI().getPath().startsWith("/api/v1/auth/")) {
                exchange.getResponseHeaders().add("Set-Cookie", "theclub_refresh=abc; Path=/api/v1/auth; HttpOnly; SameSite=Strict");
                exchange.getResponseHeaders().add("Cache-Control", "max-age=60");
            }
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    void stop() {
        server.stop(0);
    }

    void register(DynamicPropertyRegistry registry) {
        String uri = "http://localhost:" + server.getAddress().getPort();
        for (String service : new String[]{"USER", "AUTH", "ACTIVITY", "CLUB", "EVENT", "NOTIFICATION"}) {
            registry.add(service + "_SERVICE_URI", () -> uri);
        }
        registry.add("eureka.client.enabled", () -> "false");
    }
}
