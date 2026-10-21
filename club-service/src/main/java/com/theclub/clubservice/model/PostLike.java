package com.theclub.clubservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "post_likes",
        uniqueConstraints = @UniqueConstraint(name = "uk_like_post_user", columnNames = {"post_id", "user_id"}))
@Getter
@NoArgsConstructor
public class PostLike {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "post_id", length = 36, nullable = false)
    private UUID postId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_id", length = 36, nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PostLike(UUID postId, UUID userId, LocalDateTime createdAt) {
        this.id = UuidGenerator.generate();
        this.postId = postId;
        this.userId = userId;
        this.createdAt = createdAt;
    }
}
