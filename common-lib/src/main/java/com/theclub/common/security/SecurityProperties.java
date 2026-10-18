package com.theclub.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param publicPaths    ant patterns reachable without a token (in addition to actuator health).
 * @param internalApiKey shared key for service-to-service calls to /internal/**.
 */
@ConfigurationProperties(prefix = "theclub.security")
public record SecurityProperties(List<String> publicPaths, String internalApiKey) {

    public SecurityProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }
}
