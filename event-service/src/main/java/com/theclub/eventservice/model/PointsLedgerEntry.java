package com.theclub.eventservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per member per attended event; the unique key guarantees points are never awarded twice.
 */
@Entity
@Table(name = "points_ledger",
        uniqueConstraints = @UniqueConstraint(name = "uk_points_user_event", columnNames = {"user_id", "event_id"}),
        indexes = {
                @Index(name = "idx_points_awarded_at", columnList = "awarded_at"),
                @Index(name = "idx_points_club_id", columnList = "club_id")})
@Getter
@NoArgsConstructor
public class PointsLedgerEntry {

    public static final String REASON_EVENT_CHECK_IN = "EVENT_CHECK_IN";

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "club_id", length = 36, nullable = false, updatable = false)
    private UUID clubId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "event_id", length = 36, nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false)
    private int points;

    @Column(nullable = false, length = 30, updatable = false)
    private String reason;

    @Column(name = "awarded_at", nullable = false, updatable = false)
    private LocalDateTime awardedAt;

    public PointsLedgerEntry(UUID userId, UUID clubId, UUID eventId, int points, String reason, LocalDateTime awardedAt) {
        this.id = UuidGenerator.generate();
        this.userId = userId;
        this.clubId = clubId;
        this.eventId = eventId;
        this.points = points;
        this.reason = reason;
        this.awardedAt = awardedAt;
    }
}
