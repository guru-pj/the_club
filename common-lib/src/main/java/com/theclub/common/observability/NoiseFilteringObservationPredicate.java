package com.theclub.common.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Keeps traces about user traffic: drops observations for Prometheus scrapes and probes (/actuator), Eureka
 * registry heartbeats, and Spring Security's per-filter spans, which would otherwise outnumber real requests.
 * HTTP metrics for /actuator are dropped too; dashboards exclude them anyway.
 */
public class NoiseFilteringObservationPredicate implements ObservationPredicate {

    @Override
    public boolean test(String name, Observation.Context context) {
        if (name.startsWith("spring.security.")) {
            return false;
        }
        if (context instanceof ServerRequestObservationContext server && server.getCarrier() != null) {
            return !server.getCarrier().getRequestURI().startsWith("/actuator");
        }
        if (context instanceof ClientRequestObservationContext client && client.getCarrier() != null) {
            return !client.getCarrier().getURI().getPath().contains("/eureka/");
        }
        return true;
    }
}
