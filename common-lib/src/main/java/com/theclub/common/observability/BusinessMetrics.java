package com.theclub.common.observability;

import io.micrometer.core.instrument.Metrics;

/**
 * Business counters, exported to Prometheus as {@code theclub_<domain>_<event>_total{tags}}.
 * <p>
 * Example: {@code BusinessMetrics.increment("auth.logins", "outcome", "success")} becomes
 * {@code theclub_auth_logins_total{outcome="success"}}. Uses Micrometer's global registry, which Spring Boot
 * wires to the Prometheus registry, so callers need no injected dependency (and unit tests need no setup).
 * Tags must be low-cardinality (outcomes, types, statuses) — never ids or emails.
 */
public final class BusinessMetrics {

    private static final String PREFIX = "theclub.";

    private BusinessMetrics() {
    }

    public static void increment(String name, String... tags) {
        Metrics.counter(PREFIX + name, tags).increment();
    }

    public static void add(String name, double amount, String... tags) {
        if (amount > 0) {
            Metrics.counter(PREFIX + name, tags).increment(amount);
        }
    }
}
