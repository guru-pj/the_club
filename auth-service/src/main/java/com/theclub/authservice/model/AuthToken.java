package com.theclub.authservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A refresh token session. Only the SHA-256 hash of the opaque token is stored.
 */
@Entity
@Table(name = "auth_tokens", indexes = @Index(name = "idx_auth_tokens_user_id", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class AuthToken {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_id", length = 36, nullable = false)
    private UUID userId;

    @Column(name = "refresh_token_hash", length = 64, nullable = false, unique = true)
    private String refreshTokenHash;

    @Column(nullable = false)
    private LocalDateTime expiry;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AuthToken(UUID userId, String refreshTokenHash, LocalDateTime expiry, LocalDateTime createdAt) {
        this.id = UuidGenerator.generate();
        this.userId = userId;
        this.refreshTokenHash = refreshTokenHash;
        this.expiry = expiry;
        this.createdAt = createdAt;
    }
}
