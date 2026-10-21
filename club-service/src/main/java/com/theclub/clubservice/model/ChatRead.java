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

@Entity
@Table(name = "chat_reads",
        uniqueConstraints = @UniqueConstraint(name = "uk_read_conversation_user", columnNames = {"conversation_id", "user_id"}))
@Getter
@NoArgsConstructor
public class ChatRead {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "conversation_id", length = 36, nullable = false)
    private UUID conversationId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_id", length = 36, nullable = false)
    private UUID userId;

    @Setter
    @Column(name = "last_read_at", nullable = false)
    private LocalDateTime lastReadAt;

    public ChatRead(UUID conversationId, UUID userId, LocalDateTime lastReadAt) {
        this.id = UuidGenerator.generate();
        this.conversationId = conversationId;
        this.userId = userId;
        this.lastReadAt = lastReadAt;
    }
}
