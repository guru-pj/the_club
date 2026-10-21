package com.theclub.clubservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/** IDs are UUIDv7, so ordering by ID is ordering by time. */
@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "conversation_id", length = 36, nullable = false)
    private UUID conversationId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sender_id", length = 36, nullable = false)
    private UUID senderId;

    @Column(length = 2000)
    private String body;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "deleted_by", length = 36)
    private UUID deletedBy;

    public ChatMessage(UUID conversationId, UUID senderId, String body, String photoUrl, LocalDateTime createdAt) {
        this.id = UuidGenerator.generate();
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.body = body;
        this.photoUrl = photoUrl;
        this.createdAt = createdAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Hides the message for everyone; the row stays so reports about it can still be reviewed. */
    public void delete(UUID by, LocalDateTime now) {
        deletedAt = now;
        deletedBy = by;
    }
}
