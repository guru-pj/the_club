package com.theclub.authservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Operational endpoints used by Prometheus and by Docker/Kubernetes probes, and request-id correlation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false)
@ActiveProfiles("test")
class ObservabilityEndpointsTest {

    @Autowired
    MockMvc mvc;

    @Test
    void exposesPrometheusMetricsTaggedWithTheServiceName() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("application=\"auth-service\"")))
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }

    @Test
    void exposesLivenessAndReadinessProbes() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void echoesTheRequestIdAndIncludesItInErrorResponses() throws Exception {
        mvc.perform(get("/api/v1/observability-check").header("X-Request-Id", "test-req-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "test-req-1"))
                .andExpect(jsonPath("$.requestId").value("test-req-1"));
        mvc.perform(get("/api/v1/observability-check"))
                .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f-]{36}")));
    }
}
