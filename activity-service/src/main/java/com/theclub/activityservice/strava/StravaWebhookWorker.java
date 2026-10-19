package com.theclub.activityservice.strava;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.theclub.activityservice.model.StravaWebhookEventRecord;
import com.theclub.activityservice.model.StravaWebhookEventRecord.Status;
import com.theclub.activityservice.repository.StravaWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Processes queued Strava push events with retries.
 * <p>
 * <b>Multi-replica safety:</b> each due event is claimed with a conditional UPDATE (PENDING → PROCESSING with a
 * lease). Only one replica's UPDATE matches, so an event is processed once even when every replica polls. A replica
 * that dies mid-event leaves a PROCESSING row whose lease expires, and another replica reclaims it. This was chosen
 * over {@code SELECT … FOR UPDATE SKIP LOCKED} because it is portable (the H2 test database and PostgreSQL behave the
 * same) and keeps no row locks open while Strava is being called, and over ShedLock because events from different
 * athletes are processed in parallel by all replicas instead of one replica holding a global lock.
 * <p>
 * Retry backoff: 1m, 5m, 15m, then hourly; after {@value #MAX_ATTEMPTS} failed attempts the event is FAILED.
 * Rate-limit pauses requeue the event at the reset time without counting an attempt.
 */
@Component
public class StravaWebhookWorker {

    static final int MAX_ATTEMPTS = 6;
    static final Duration LEASE = Duration.ofMinutes(5);
    static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(15), Duration.ofHours(1));
    static final Duration RETENTION = Duration.ofDays(30);
    private static final Logger log = LoggerFactory.getLogger(StravaWebhookWorker.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final StravaWebhookEventRepository events;
    private final StravaWebhookProcessor processor;
    private final StravaProperties properties;
    private final StravaMetrics metrics;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public StravaWebhookWorker(StravaWebhookEventRepository events, StravaWebhookProcessor processor,
                               StravaProperties properties, StravaMetrics metrics, ObjectMapper json,
                               PlatformTransactionManager transactionManager, Clock clock) {
        this.events = events;
        this.processor = processor;
        this.properties = properties;
        this.metrics = metrics;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public record Summary(int processed, int ignored, int retried, int failed, int requeued) {
    }

    @Scheduled(fixedDelayString = "${theclub.strava.worker-interval:PT10S}",
            initialDelayString = "${theclub.strava.worker-initial-delay:PT20S}")
    public void scheduledRun() {
        if (!properties.workerEnabled()) {
            return;
        }
        Summary summary = runOnce();
        if (summary.processed() + summary.retried() + summary.failed() + summary.requeued() > 0) {
            log.info("Strava webhook worker: {}", summary);
        }
    }

    /** Processes up to one batch of due events. */
    public Summary runOnce() {
        Instant now = clock.instant();
        List<UUID> due = events.findDueIds(now, PageRequest.of(0, properties.workerBatchSize()));
        int processed = 0;
        int ignored = 0;
        int retried = 0;
        int failed = 0;
        int requeued = 0;
        for (UUID id : due) {
            Integer claimed = transactions.execute(status -> events.claim(id, now, now.plus(LEASE)));
            if (claimed == null || claimed == 0) {
                continue; // another replica took it
            }
            StravaWebhookEventRecord record = events.findById(id).orElse(null);
            if (record == null) {
                continue;
            }
            try {
                StravaWebhookProcessor.Outcome outcome = processor.process(toEvent(record));
                finish(id, outcome == StravaWebhookProcessor.Outcome.DONE ? Status.DONE : Status.IGNORED, null);
                metrics.webhookEvent(record.getAspectType(), outcome == StravaWebhookProcessor.Outcome.DONE
                        ? "done" : "ignored");
                if (outcome == StravaWebhookProcessor.Outcome.DONE) {
                    processed++;
                } else {
                    ignored++;
                }
            } catch (StravaRateLimitedException e) {
                requeue(id, e.resumeAt());
                metrics.webhookEvent(record.getAspectType(), "rate_limited");
                requeued++;
                // Everything else in this batch would hit the same pause; stop early
                break;
            } catch (RuntimeException e) {
                if (scheduleRetry(id, e)) {
                    metrics.webhookEvent(record.getAspectType(), "retry");
                    retried++;
                } else {
                    metrics.webhookEvent(record.getAspectType(), "failed");
                    failed++;
                }
            }
        }
        purgeOld(now);
        return new Summary(processed, ignored, retried, failed, requeued);
    }

    private void finish(UUID id, Status status, String error) {
        transactions.executeWithoutResult(tx -> events.findById(id).ifPresent(record -> {
            record.setStatus(status);
            record.setLeaseUntil(null);
            record.setLastError(error);
            record.setProcessedAt(clock.instant());
            events.save(record);
        }));
    }

    private void requeue(UUID id, Instant at) {
        transactions.executeWithoutResult(tx -> events.findById(id).ifPresent(record -> {
            record.setStatus(Status.PENDING);
            record.setLeaseUntil(null);
            record.setNextAttemptAt(at);
            events.save(record);
        }));
    }

    /** @return true when another attempt was scheduled, false when the event is now FAILED */
    private boolean scheduleRetry(UUID id, RuntimeException error) {
        String message = truncate(error.getClass().getSimpleName() + ": " + error.getMessage(), 1000);
        Boolean retry = transactions.execute(tx -> events.findById(id).map(record -> {
            int attempts = record.getAttempts() + 1;
            record.setAttempts(attempts);
            record.setLastError(message);
            record.setLeaseUntil(null);
            if (attempts >= MAX_ATTEMPTS) {
                record.setStatus(Status.FAILED);
                record.setProcessedAt(clock.instant());
                log.warn("Strava {} {} event for object {} failed permanently after {} attempts: {}",
                        record.getObjectType(), record.getAspectType(), record.getObjectId(), attempts, message);
                events.save(record);
                return false;
            }
            record.setStatus(Status.PENDING);
            record.setNextAttemptAt(clock.instant().plus(BACKOFF.get(Math.min(attempts - 1, BACKOFF.size() - 1))));
            events.save(record);
            return true;
        }).orElse(false));
        return Boolean.TRUE.equals(retry);
    }

    private void purgeOld(Instant now) {
        transactions.executeWithoutResult(tx ->
                events.deleteFinishedBefore(EnumSet.of(Status.DONE, Status.IGNORED), now.minus(RETENTION)));
    }

    private StravaWebhookEvent toEvent(StravaWebhookEventRecord record) {
        Map<String, Object> updates = null;
        if (record.getUpdates() != null) {
            try {
                updates = json.readValue(record.getUpdates(), MAP);
            } catch (Exception e) {
                updates = Map.of();
            }
        }
        return new StravaWebhookEvent(record.getObjectType(), record.getObjectId(), record.getAspectType(),
                record.getOwnerId(), updates, record.getEventTime(), record.getSubscriptionId());
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    /** Turns on the schedule unless theclub.strava.worker-enabled=false (tests drive {@link #runOnce()} directly). */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(prefix = "theclub.strava", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfig {
    }
}
