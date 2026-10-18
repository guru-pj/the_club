package com.theclub.gatewayservice.observability;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.web.reactive.function.client.ClientRequestObservationContext;

/**
 * Same noise filter as the servlet services (common-lib): no traces for /actuator (scrapes, probes) or Eureka
 * heartbeats, so traces show user traffic.
 */
@Configuration(proxyBeanMethods = false)
public class GatewayObservationConfig {

    @Bean
    public ObservationPredicate noiseFilteringObservationPredicate() {
        return (name, context) -> {
            if (name.startsWith("spring.security.")) {
                return false;
            }
            if (context instanceof ServerRequestObservationContext server && server.getCarrier() != null) {
                return !server.getCarrier().getPath().value().startsWith("/actuator");
            }
            // WebClient calls (reactive) ...
            if (context instanceof ClientRequestObservationContext client) {
                return client.getRequest() == null || !client.getRequest().url().getPath().contains("/eureka/");
            }
            // ... and the Eureka client's own RestTemplate (blocking) calls
            if (context instanceof org.springframework.http.client.observation.ClientRequestObservationContext client
                    && client.getCarrier() != null) {
                return !client.getCarrier().getURI().getPath().contains("/eureka/");
            }
            return true;
        };
    }
}
