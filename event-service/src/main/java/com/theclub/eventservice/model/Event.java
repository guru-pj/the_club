package com.theclub.eventservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "events", indexes = @Index(name = "idx_events_club_id", columnList = "club_id"))
@Getter
@Setter
@NoArgsConstructor
public class Event {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "club_id", length = 36, nullable = false, updatable = false)
    private UUID clubId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "created_by", length = 36, nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String location;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "registration_opens_at", nullable = false)
    private LocalDateTime registrationOpensAt;

    /** Null means registration stays open until the event starts. */
    @Column(name = "registration_closes_at")
    private LocalDateTime registrationClosesAt;

    /** Null means unlimited. */
    private Integer capacity;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** Rules and instructions for participants (what to bring, pace groups, cut-off times). */
    @Column(columnDefinition = "TEXT")
    private String rules;

    /** Only the club's subscribers (members) may register; otherwise anyone signed in may. */
    @Column(name = "subscribers_only", nullable = false)
    private boolean subscribersOnly;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    /** Points awarded to each member on check-in. */
    @Column(nullable = false)
    private int points = 10;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status = EventStatus.SCHEDULED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private long version;

    public Event(UUID clubId, UUID createdBy) {
        this.id = UuidGenerator.generate();
        this.clubId = clubId;
        this.createdBy = createdBy;
    }

    public LocalDateTime effectiveRegistrationClosesAt() {
        return registrationClosesAt != null ? registrationClosesAt : startTime;
    }

    public boolean isFree() {
        return price == null || price.signum() == 0;
    }

    public RegistrationState registrationState(LocalDateTime now, Long spotsLeft) {
        if (status == EventStatus.CANCELLED) {
            return RegistrationState.CANCELLED;
        }
        if (status == EventStatus.COMPLETED || !now.isBefore(startTime)) {
            return RegistrationState.PAST;
        }
        if (now.isBefore(registrationOpensAt)) {
            return RegistrationState.NOT_YET_OPEN;
        }
        if (!now.isBefore(effectiveRegistrationClosesAt())) {
            return RegistrationState.CLOSED;
        }
        if (spotsLeft != null && spotsLeft <= 0) {
            return RegistrationState.FULL;
        }
        return RegistrationState.OPEN;
    }

    public boolean isRegistrationOpen(LocalDateTime now) {
        return status == EventStatus.SCHEDULED
                && !now.isBefore(registrationOpensAt)
                && now.isBefore(effectiveRegistrationClosesAt());
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
