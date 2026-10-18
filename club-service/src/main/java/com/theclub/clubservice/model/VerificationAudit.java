package com.theclub.clubservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only history of every action taken on a verification request.
 */
@Entity
@Table(name = "owner_verification_audit", indexes = @Index(name = "idx_audit_request_id", columnList = "request_id"))
@Getter
@NoArgsConstructor
public class VerificationAudit {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_id", length = 36, nullable = false)
    private UUID requestId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "actor_id", length = 36, nullable = false)
    private UUID actorId;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public VerificationAudit(UUID requestId, UUID actorId, String action, String note) {
        this.id = UuidGenerator.generate();
        this.requestId = requestId;
        this.actorId = actorId;
        this.action = action;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
