package com.theclub.common.observability;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessMetricsTest {

    @Test
    void countsUnderTheClubPrefixWithTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);
        try {
            BusinessMetrics.increment("auth.logins", "outcome", "success");
            BusinessMetrics.increment("auth.logins", "outcome", "success");
            BusinessMetrics.add("auth.sessions.revoked", 3);
            BusinessMetrics.add("auth.sessions.revoked", 0);

            assertThat(registry.get("theclub.auth.logins").tag("outcome", "success").counter().count()).isEqualTo(2);
            assertThat(registry.get("theclub.auth.sessions.revoked").counter().count()).isEqualTo(3);
        } finally {
            Metrics.removeRegistry(registry);
        }
    }
}
