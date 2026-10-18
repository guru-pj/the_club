package com.theclub.gatewayservice.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Adds defensive headers to every API response, whatever the downstream service returned.
 * Token responses (/api/v1/auth/**) must never be cached by browsers or intermediaries.
 * Set-Cookie and other downstream headers are left untouched.
 */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/api/")) {
            exchange.getResponse().beforeCommit(() -> {
                HttpHeaders headers = exchange.getResponse().getHeaders();
                headers.set("X-Content-Type-Options", "nosniff");
                headers.set("Referrer-Policy", "no-referrer");
                if (path.startsWith("/api/v1/auth/")) {
                    headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
                    headers.set(HttpHeaders.PRAGMA, "no-cache");
                }
                return Mono.empty();
            });
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
