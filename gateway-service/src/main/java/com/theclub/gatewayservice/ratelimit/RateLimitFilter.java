package com.theclub.gatewayservice.ratelimit;

import com.theclub.gatewayservice.config.ClientIpResolver;
import com.theclub.gatewayservice.config.GatewayHardeningProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Per-client-IP rate limiting: a strict bucket for credential/account-recovery POSTs (brute force, credential
 * stuffing, email flooding) and a general bucket for every API call. Exceeding either returns 429 + Retry-After.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final boolean enabled;
    private final ClientIpResolver clientIp;
    private final List<PathPattern> strictPaths;
    private final RateLimiter strict;
    private final RateLimiter general;

    public RateLimitFilter(GatewayHardeningProperties properties, ClientIpResolver clientIp) {
        GatewayHardeningProperties.RateLimit config = properties.rateLimit();
        this.enabled = config.enabled();
        this.clientIp = clientIp;
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.strictPaths = config.strictPaths().stream().map(parser::parse).toList();
        long idle = config.idleEviction().toNanos();
        this.strict = new RateLimiter(config.strict(), config.maxTrackedClients(), idle, System::nanoTime);
        this.general = new RateLimiter(config.general(), config.maxTrackedClients(), idle, System::nanoTime);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!enabled || !path.startsWith("/api/")) {
            return chain.filter(exchange);
        }
        String ip = clientIp.resolve(exchange.getRequest());
        if (exchange.getRequest().getMethod() == HttpMethod.POST && isStrict(path)) {
            long wait = strict.tryAcquire(ip);
            if (wait > 0) {
                return tooManyRequests(exchange.getResponse(), wait);
            }
        }
        long wait = general.tryAcquire(ip);
        if (wait > 0) {
            return tooManyRequests(exchange.getResponse(), wait);
        }
        return chain.filter(exchange);
    }

    private boolean isStrict(String path) {
        PathContainer container = PathContainer.parsePath(path);
        return strictPaths.stream().anyMatch(p -> p.matches(container));
    }

    private static Mono<Void> tooManyRequests(ServerHttpResponse response, long waitNanos) {
        long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(waitNanos + TimeUnit.SECONDS.toNanos(1) - 1));
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        byte[] body = ("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"detail\":\"Too many requests. Try again in " + retryAfter + " seconds.\"}")
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // right after BlockInternalPathsFilter, before routing does any work
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
