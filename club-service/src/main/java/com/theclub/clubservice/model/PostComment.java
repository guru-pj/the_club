package com.theclub.clubservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "post_comments", indexes = @Index(name = "idx_comments_post_created", columnList = "post_id, created_at"))
@Getter
@NoArgsConstructor
public class PostComment {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "post_id", length = 36, nullable = false)
    private UUID postId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "author_id", length = 36, nullable = false)
    private UUID authorId;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PostComment(UUID postId, UUID authorId, String content, LocalDateTime createdAt) {
        this.id = UuidGenerator.generate();
        this.postId = postId;
        this.authorId = authorId;
        this.content = content;
        this.createdAt = createdAt;
    }
}
