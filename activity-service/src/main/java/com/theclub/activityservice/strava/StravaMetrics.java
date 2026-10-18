package com.theclub.activityservice.strava;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Strava integration metrics. Prometheus names: theclub_strava_api_calls_total{endpoint,outcome},
 * theclub_strava_webhook_events_total{aspect,outcome}, theclub_strava_ratelimit_usage{window,kind} and
 * theclub_strava_ratelimit_limit{window,kind}. No-ops when no MeterRegistry is present.
 */
@Component
public class StravaMetrics {

    private final MeterRegistry registry;
    private final AtomicLong[] usage = {new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong()};
    private final AtomicLong[] limits = {new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong()};

    public StravaMetrics(ObjectProvider<MeterRegistry> registry) {
        this.registry = registry.getIfAvailable();
        if (this.registry != null) {
            String[][] series = {{"15min", "overall"}, {"daily", "overall"}, {"15min", "read"}, {"daily", "read"}};
            for (int i = 0; i < series.length; i++) {
                Gauge.builder("theclub.strava.ratelimit.usage", usage[i], AtomicLong::get)
                        .description("Requests used in the current Strava rate-limit window")
                        .tags("window", series[i][0], "kind", series[i][1]).register(this.registry);
                Gauge.builder("theclub.strava.ratelimit.limit", limits[i], AtomicLong::get)
                        .description("Strava rate limit for the window")
                        .tags("window", series[i][0], "kind", series[i][1]).register(this.registry);
            }
        }
    }

    public void apiCall(String endpoint, String outcome) {
        if (registry != null) {
            Counter.builder("theclub.strava.api.calls").tags("endpoint", endpoint, "outcome", outcome)
                    .register(registry).increment();
        }
    }

    public void webhookEvent(String aspect, String outcome) {
        if (registry != null) {
            Counter.builder("theclub.strava.webhook.events").tags("aspect", aspect == null ? "unknown" : aspect,
                    "outcome", outcome).register(registry).increment();
        }
    }

    /** index: 0 = 15min overall, 1 = daily overall, 2 = 15min read, 3 = daily read */
    void rateLimit(int index, long used, long limit) {
        usage[index].set(used);
        limits[index].set(limit);
    }
}
