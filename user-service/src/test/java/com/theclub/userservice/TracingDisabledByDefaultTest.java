package com.theclub.userservice;

import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Without TRACING_ENABLED nothing is exported, so services don't try to reach a collector that isn't running.
 */
@SpringBootTest
@AutoConfigureTracing
@ActiveProfiles("test")
class TracingDisabledByDefaultTest {

    @Autowired
    ApplicationContext context;

    @Test
    void createsNoSpanExporter() {
        assertThat(context.getBeansOfType(SpanExporter.class)).isEmpty();
    }
}
