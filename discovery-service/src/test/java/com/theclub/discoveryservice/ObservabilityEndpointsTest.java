package com.theclub.discoveryservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@AutoConfigureMetrics
class ObservabilityEndpointsTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void exposesPrometheusMetricsAndProbes() {
        assertThat(rest.getForObject("/actuator/prometheus", String.class)).contains("application=\"discovery-service\"");
        assertThat(rest.getForEntity("/actuator/health/liveness", String.class).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(rest.getForEntity("/actuator/health/readiness", String.class).getStatusCode().is2xxSuccessful()).isTrue();
    }
}
