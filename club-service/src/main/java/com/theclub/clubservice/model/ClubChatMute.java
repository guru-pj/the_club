package com.theclub.clubservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/** Someone the club owner muted in the club chat: they can read it but not write. */
@Entity
@Table(name = "club_chat_mutes",
        uniqueConstraints = @UniqueConstraint(name = "uk_mute_club_user", columnNames = {"club_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ClubChatMute {

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

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "muted_by", length = 36, nullable = false)
    private UUID mutedBy;

    /** null: until the owner unmutes them */
    @Column(name = "muted_until")
    private LocalDateTime mutedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ClubChatMute(UUID clubId, UUID userId, UUID mutedBy, LocalDateTime mutedUntil, LocalDateTime createdAt) {
        this.id = UuidGenerator.generate();
        this.clubId = clubId;
        this.userId = userId;
        this.mutedBy = mutedBy;
        this.mutedUntil = mutedUntil;
        this.createdAt = createdAt;
    }

    public boolean activeAt(LocalDateTime now) {
        return mutedUntil == null || mutedUntil.isAfter(now);
    }
}
