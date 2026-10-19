package com.theclub.activityservice.strava;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.theclub.activityservice.model.StravaWebhookEventRecord;
import com.theclub.activityservice.repository.StravaWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Persists incoming Strava push events so the webhook can answer immediately (Strava expects a 200 within
 * 2 seconds and retries otherwise) and nothing is lost if the service restarts before processing.
 */
@Component
public class StravaWebhookInbox {

    static final int MAX_UPDATES_LENGTH = 2000;
    private static final Logger log = LoggerFactory.getLogger(StravaWebhookInbox.class);

    private final StravaWebhookEventRepository events;
    private final ObjectMapper json;
    private final StravaMetrics metrics;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public StravaWebhookInbox(StravaWebhookEventRepository events, ObjectMapper json, StravaMetrics metrics,
                              PlatformTransactionManager transactionManager, Clock clock) {
        this.events = events;
        this.json = json;
        this.metrics = metrics;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * @return true when stored, false when it was a duplicate delivery (or not a Strava event shape)
     */
    public boolean accept(StravaWebhookEvent event) {
        if (event.objectType() == null || event.aspectType() == null) {
            metrics.webhookEvent(event.aspectType(), "invalid");
            return false;
        }
        if (events.existsByObjectTypeAndObjectIdAndAspectTypeAndEventTime(event.objectType(), event.objectId(),
                event.aspectType(), event.eventTime())) {
            metrics.webhookEvent(event.aspectType(), "duplicate");
            return false;
        }
        StravaWebhookEventRecord record = new StravaWebhookEventRecord(event.objectType(), event.objectId(),
                event.aspectType(), event.ownerId(), updatesJson(event), event.eventTime(), event.subscriptionId(),
                clock.instant());
        try {
            transactions.executeWithoutResult(status -> events.saveAndFlush(record));
        } catch (DataIntegrityViolationException e) {
            // Concurrent re-delivery won the race on the unique key
            metrics.webhookEvent(event.aspectType(), "duplicate");
            return false;
        }
        metrics.webhookEvent(event.aspectType(), "received");
        return true;
    }

    private String updatesJson(StravaWebhookEvent event) {
        if (event.updates() == null || event.updates().isEmpty()) {
            return null;
        }
        try {
            String value = json.writeValueAsString(event.updates());
            return value.length() > MAX_UPDATES_LENGTH ? value.substring(0, MAX_UPDATES_LENGTH) : value;
        } catch (JacksonException e) {
            log.debug("Unserializable Strava updates map ignored");
            return null;
        }
    }
}
