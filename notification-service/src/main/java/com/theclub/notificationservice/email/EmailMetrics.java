package com.theclub.notificationservice.email;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Email delivery metrics (Prometheus names: theclub_emails_sent_total, theclub_emails_failed_total,
 * theclub_email_outbox_pending). No-ops when no MeterRegistry is present.
 */
@Component
public class EmailMetrics {

    private final MeterRegistry registry;
    private final AtomicLong pending = new AtomicLong();

    public EmailMetrics(ObjectProvider<MeterRegistry> registry) {
        this.registry = registry.getIfAvailable();
        if (this.registry != null) {
            Gauge.builder("theclub.email.outbox.pending", pending, AtomicLong::get)
                    .description("Emails waiting in the outbox for delivery")
                    .register(this.registry);
        }
    }

    public void sent(String provider, String template) {
        if (registry != null) {
            Counter.builder("theclub.emails.sent").description("Emails accepted by the mail provider")
                    .tag("provider", provider).tag("template", template == null ? "raw" : template)
                    .register(registry).increment();
        }
    }

    /**
     * @param reason {@code retry} (will be attempted again), {@code permanent}, {@code exhausted} or {@code suppressed}.
     */
    public void failed(String provider, String reason) {
        if (registry != null) {
            Counter.builder("theclub.emails.failed").description("Failed email delivery attempts")
                    .tag("provider", provider).tag("reason", reason)
                    .register(registry).increment();
        }
    }

    public void pending(long count) {
        pending.set(count);
    }
}
