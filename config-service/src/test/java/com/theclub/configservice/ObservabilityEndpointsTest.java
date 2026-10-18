package com.theclub.configservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability(tracing = false)
class ObservabilityEndpointsTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void exposesPrometheusMetricsAndProbes() {
        assertThat(rest.getForObject("/actuator/prometheus", String.class)).contains("application=\"config-service\"");
        assertThat(rest.getForEntity("/actuator/health/liveness", String.class).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(rest.getForEntity("/actuator/health/readiness", String.class).getStatusCode().is2xxSuccessful()).isTrue();
    }
}
