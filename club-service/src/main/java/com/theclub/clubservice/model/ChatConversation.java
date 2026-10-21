package com.theclub.clubservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/** A club's group chat ({@code clubId} set) or a direct conversation between two people. */
@Entity
@Table(name = "chat_conversations")
@Getter
@NoArgsConstructor
public class ChatConversation {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ConversationKind kind;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "club_id", length = 36)
    private UUID clubId;

    /** The pair of a direct conversation, ordered so each pair has a single row. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_low", length = 36)
    private UUID userLow;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_high", length = 36)
    private UUID userHigh;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Setter
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    public static ChatConversation group(UUID clubId, LocalDateTime now) {
        ChatConversation c = new ChatConversation();
        c.id = UuidGenerator.generate();
        c.kind = ConversationKind.GROUP;
        c.clubId = clubId;
        c.createdAt = now;
        return c;
    }

    public static ChatConversation direct(UUID a, UUID b, LocalDateTime now) {
        ChatConversation c = new ChatConversation();
        c.id = UuidGenerator.generate();
        c.kind = ConversationKind.DIRECT;
        c.userLow = lower(a, b);
        c.userHigh = c.userLow.equals(a) ? b : a;
        c.createdAt = now;
        return c;
    }

    /** UUIDs compare by their string form here, matching how the CHAR(36) columns sort. */
    public static UUID lower(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) <= 0 ? a : b;
    }

    public boolean isGroup() {
        return kind == ConversationKind.GROUP;
    }

    public boolean hasParticipant(UUID userId) {
        return userId.equals(userLow) || userId.equals(userHigh);
    }

    /** The other person of a direct conversation. */
    public UUID otherThan(UUID userId) {
        return userId.equals(userLow) ? userHigh : userLow;
    }
}
