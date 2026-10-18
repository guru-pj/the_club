package com.theclub.common.observability;

import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class NoiseFilteringObservationPredicateTest {

    private final NoiseFilteringObservationPredicate predicate = new NoiseFilteringObservationPredicate();

    private static ServerRequestObservationContext server(String uri) {
        return new ServerRequestObservationContext(new MockHttpServletRequest("GET", uri), new MockHttpServletResponse());
    }

    private static ClientRequestObservationContext client(String url) {
        return new ClientRequestObservationContext(new MockClientHttpRequest(HttpMethod.PUT, URI.create(url)));
    }

    @Test
    void dropsScrapesProbesHeartbeatsAndSecurityInternals() {
        assertThat(predicate.test("http.server.requests", server("/actuator/prometheus"))).isFalse();
        assertThat(predicate.test("http.server.requests", server("/actuator/health/readiness"))).isFalse();
        assertThat(predicate.test("http.client.requests", client("http://discovery-service:8761/eureka/apps/USER-SERVICE/1"))).isFalse();
        assertThat(predicate.test("spring.security.filterchains", new Observation.Context())).isFalse();
    }

    @Test
    void keepsUserTraffic() {
        assertThat(predicate.test("http.server.requests", server("/api/v1/users/me"))).isTrue();
        assertThat(predicate.test("http.client.requests", client("http://user-service/internal/users/x"))).isTrue();
        assertThat(predicate.test("tasks.scheduled.execution", new Observation.Context())).isTrue();
    }
}
