package com.theclub.clubservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/** A user following a club: they get its posts and event announcements without being a member. */
@Entity
@Table(name = "club_follows",
        uniqueConstraints = @UniqueConstraint(name = "uk_follow_club_user", columnNames = {"club_id", "user_id"}),
        indexes = @Index(name = "idx_follow_user_id", columnList = "user_id"))
@Getter
@NoArgsConstructor
public class ClubFollow {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "club_id", length = 36, nullable = false)
    private UUID clubId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_id", length = 36, nullable = false)
    private UUID userId;

    @Column(name = "followed_at", nullable = false, updatable = false)
    private LocalDateTime followedAt;

    public ClubFollow(UUID clubId, UUID userId) {
        this.id = UuidGenerator.generate();
        this.clubId = clubId;
        this.userId = userId;
    }

    @PrePersist
    void onCreate() {
        followedAt = LocalDateTime.now();
    }
}
