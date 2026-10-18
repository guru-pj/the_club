package com.theclub.activityservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A Strava push event persisted on receipt and processed later by {@code StravaWebhookWorker}.
 */
@Entity
@Table(name = "strava_webhook_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_strava_webhook_event",
                columnNames = {"object_type", "object_id", "aspect_type", "event_time"}),
        indexes = @Index(name = "idx_strava_webhook_due", columnList = "status, next_attempt_at"))
@Getter
@Setter
@NoArgsConstructor
public class StravaWebhookEventRecord {

    public enum Status {
        PENDING,
        /** Claimed by a worker until {@code leaseUntil}; reclaimed if the worker died. */
        PROCESSING,
        DONE,
        /** Retries exhausted. */
        FAILED,
        /** Not relevant (unknown athlete, unsupported object type, integration disabled). */
        IGNORED
    }

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "object_type", nullable = false, length = 20)
    private String objectType;

    @Column(name = "object_id", nullable = false)
    private long objectId;

    @Column(name = "aspect_type", nullable = false, length = 20)
    private String aspectType;

    @Column(name = "owner_id", nullable = false)
    private long ownerId;

    /** JSON of Strava's "updates" map (title, type, private, authorized …). */
    @Column(length = 2000)
    private String updates;

    @Column(name = "event_time", nullable = false)
    private long eventTime;

    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    public StravaWebhookEventRecord(String objectType, long objectId, String aspectType, long ownerId,
                                    String updates, long eventTime, Long subscriptionId, Instant now) {
        this.id = UuidGenerator.generate();
        this.objectType = objectType;
        this.objectId = objectId;
        this.aspectType = aspectType;
        this.ownerId = ownerId;
        this.updates = updates;
        this.eventTime = eventTime;
        this.subscriptionId = subscriptionId;
        this.status = Status.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.receivedAt = now;
    }
}
