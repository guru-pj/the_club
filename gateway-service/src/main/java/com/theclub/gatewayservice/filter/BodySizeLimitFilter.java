package com.theclub.gatewayservice.filter;

import com.theclub.gatewayservice.config.GatewayHardeningProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rejects request bodies above {@code theclub.gateway.max-body-bytes} with 413. Declared lengths are checked up
 * front; bodies without Content-Length (chunked, HTTP/2) are counted while being buffered (never more than the
 * limit is held in memory) and then forwarded.
 */
@Component
public class BodySizeLimitFilter implements GlobalFilter, Ordered {

    private final long maxBytes;
    private final long maxUploadBytes;

    public BodySizeLimitFilter(GatewayHardeningProperties properties) {
        this.maxBytes = properties.maxBodyBytes();
        this.maxUploadBytes = properties.maxUploadBodyBytes();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        // uploads are multipart/form-data; everything else (JSON) keeps the small limit
        long maxBytes = MediaType.MULTIPART_FORM_DATA.isCompatibleWith(request.getHeaders().getContentType())
                ? maxUploadBytes : this.maxBytes;
        long declared = request.getHeaders().getContentLength();
        if (declared > maxBytes) {
            return payloadTooLarge(exchange.getResponse(), maxBytes);
        }
        if (declared >= 0) {
            return chain.filter(exchange);
        }
        ServerWebExchange counted = exchange.mutate().request(new CountingRequest(request, maxBytes)).build();
        return ServerWebExchangeUtils.cacheRequestBody(counted,
                        cached -> chain.filter(counted.mutate().request(cached).build()))
                .onErrorResume(BodyTooLargeException.class, e -> payloadTooLarge(exchange.getResponse(), maxBytes));
    }

    private Mono<Void> payloadTooLarge(ServerHttpResponse response, long maxBytes) {
        response.setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] body = ("{\"type\":\"about:blank\",\"title\":\"Payload Too Large\",\"status\":413,"
                + "\"detail\":\"Request body exceeds " + maxBytes + " bytes\"}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    /** Fails the body stream as soon as more than {@code maxBytes} have arrived. */
    private static final class CountingRequest extends ServerHttpRequestDecorator {

        private final long maxBytes;

        CountingRequest(ServerHttpRequest delegate, long maxBytes) {
            super(delegate);
            this.maxBytes = maxBytes;
        }

        @Override
        public Flux<DataBuffer> getBody() {
            AtomicLong total = new AtomicLong();
            return super.getBody().handle((buffer, sink) -> {
                if (total.addAndGet(buffer.readableByteCount()) > maxBytes) {
                    DataBufferUtils.release(buffer);
                    sink.error(new BodyTooLargeException());
                } else {
                    sink.next(buffer);
                }
            });
        }
    }

    private static final class BodyTooLargeException extends RuntimeException {
        BodyTooLargeException() {
            super(null, null, false, false);
        }
    }
}
