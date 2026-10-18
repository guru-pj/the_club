package com.theclub.gatewayservice.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request a correlation id before anything else runs (including rate limiting and routing):
 * a valid incoming X-Request-Id is kept, otherwise a new one is generated. It is forwarded to the services,
 * which log it and include it in error responses, and returned to the client.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdWebFilter implements WebFilter {

    public static final String HEADER = "X-Request-Id";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String requestId = incoming != null && VALID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        ServerWebExchange withId = exchange.mutate()
                .request(r -> r.headers(h -> h.set(HEADER, requestId)))
                .build();
        // set just before the response is committed: replaces the copy echoed back by the downstream service,
        // so the client gets exactly one X-Request-Id
        withId.getResponse().beforeCommit(() -> {
            withId.getResponse().getHeaders().set(HEADER, requestId);
            return Mono.empty();
        });
        return chain.filter(withId);
    }
}
