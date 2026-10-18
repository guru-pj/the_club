package com.theclub.activityservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "activities",
        uniqueConstraints = @UniqueConstraint(name = "uk_activity_source_external", columnNames = {"source", "external_id"}),
        indexes = {
                @Index(name = "idx_activity_user", columnList = "user_id"),
                @Index(name = "idx_activity_user_start", columnList = "user_id, start_time")
        })
@Getter
@Setter
@NoArgsConstructor
public class Activity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_id", length = 36, nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActivityType type;

    /** Kilometres. */
    @Column(nullable = false)
    private double distance;

    /** Moving time in seconds. */
    @Column(nullable = false)
    private int duration;

    private Integer calories;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivitySource source;

    /** Strava activity id for imported activities. */
    @Column(name = "external_id", length = 64)
    private String externalId;

    /** Total time including pauses, in seconds (Strava elapsed_time); {@link #duration} is moving time. */
    @Column(name = "elapsed_time")
    private Integer elapsedTime;

    /** Metres climbed. */
    @Column(name = "total_elevation_gain")
    private Double totalElevationGain;

    @Column(name = "average_heartrate")
    private Double averageHeartrate;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    /**
     * Private on Strava ("only me"). Excluded from club leaderboards unless the athlete opted in
     * ({@link StravaConnection#isIncludePrivate()}).
     */
    @Column(name = "is_private", nullable = false)
    private boolean privateActivity;

    /** Absolute start instant; {@link #startTime} is the athlete's local wall-clock time. */
    @Column(name = "start_time_utc")
    private Instant startTimeUtc;

    /** IANA zone of the activity, e.g. "Asia/Kolkata", when Strava provides one. */
    @Column(name = "time_zone", length = 64)
    private String timeZone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Activity(UUID userId, ActivitySource source) {
        this.id = UuidGenerator.generate();
        this.userId = userId;
        this.source = source;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
