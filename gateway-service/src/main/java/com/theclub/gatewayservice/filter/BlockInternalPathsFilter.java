package com.theclub.gatewayservice.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Locale;

/**
 * Service-to-service endpoints live under /internal/** and must never be reachable through the gateway,
 * even via an encoded or dot-segment path that a route predicate might otherwise match.
 * Also strips the internal key header so clients cannot forward one.
 */
@Component
public class BlockInternalPathsFilter implements GlobalFilter, Ordered {

    static final String INTERNAL_KEY_HEADER = "X-Internal-Key";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String rawPath = exchange.getRequest().getURI().getRawPath().toLowerCase(Locale.ROOT);
        if (rawPath.contains("/internal") || rawPath.contains("%2f") || rawPath.contains("%2e") || rawPath.contains("/..")) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        ServerWebExchange sanitized = exchange.mutate()
                .request(r -> r.headers(h -> h.remove(INTERNAL_KEY_HEADER)))
                .build();
        return chain.filter(sanitized);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
