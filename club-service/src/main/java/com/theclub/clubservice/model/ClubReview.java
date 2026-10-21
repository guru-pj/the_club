package com.theclub.clubservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/** One person's rating (1–5) and comment for a club, with an optional reply from the owner. */
@Entity
@Table(name = "club_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_review_club_user", columnNames = {"club_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ClubReview {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "club_id", length = 36, nullable = false, updatable = false)
    private UUID clubId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private int rating;

    @Column(length = 2000)
    private String comment;

    @Column(name = "owner_reply", length = 1000)
    private String ownerReply;

    @Column(name = "owner_reply_at")
    private LocalDateTime ownerReplyAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ClubReview(UUID clubId, UUID userId, int rating, String comment) {
        this.id = UuidGenerator.generate();
        this.clubId = clubId;
        this.userId = userId;
        this.rating = rating;
        this.comment = comment;
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
