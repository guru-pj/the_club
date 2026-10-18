package com.theclub.gatewayservice.config;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

/**
 * Determines the client IP used for rate limiting.
 * <p>
 * Behind nginx the TCP peer is always the proxy, so with {@code trust-forwarded-for} enabled the IP comes from
 * X-Real-IP (set by nginx to the peer it saw) or, failing that, the right-most X-Forwarded-For entry — the one
 * appended by the trusted proxy. Left-most entries are client-controlled and never trusted.
 */
@Component
public class ClientIpResolver {

    private final boolean trustForwardedFor;

    public ClientIpResolver(GatewayHardeningProperties properties) {
        this.trustForwardedFor = properties.trustForwardedFor();
    }

    public String resolve(ServerHttpRequest request) {
        if (trustForwardedFor) {
            String realIp = request.getHeaders().getFirst("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
            String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] hops = forwarded.split(",");
                return hops[hops.length - 1].trim();
            }
        }
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null) {
            return "unknown";
        }
        return remote.getAddress() != null ? remote.getAddress().getHostAddress() : remote.getHostString();
    }
}
