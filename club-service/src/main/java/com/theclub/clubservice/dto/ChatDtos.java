package com.theclub.clubservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.theclub.clubservice.model.ChatReportStatus;
import com.theclub.clubservice.model.ConversationKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

/** Requests and responses of the chat API. */
public final class ChatDtos {

    private ChatDtos() {
    }

    /** Text, a photo (uploaded with {@code POST /api/v1/chat/photos}), or both. */
    public record SendMessageRequest(@Size(max = 2000) String body, @Size(max = 500) String photoUrl) {
    }

    public record StartDirectRequest(@NotNull UUID userId) {
    }

    public record ReportRequest(@Size(max = 500) String reason) {
    }

    /** @param hours how long; empty means until unmuted */
    public record MuteRequest(@Min(1) @Max(720) Integer hours) {
    }

    /** @param removeMessage true removes the message for everyone; false keeps it */
    public record ResolveReportRequest(boolean removeMessage) {
    }

    /** A deleted message keeps its place but has no body or photo. */
    public record Message(UUID id, UUID conversationId, UUID senderId, String senderName, String senderPhotoUrl,
                          String body, String photoUrl, boolean deleted, LocalDateTime createdAt) {
    }

    /**
     * One entry of the inbox: a club's group chat or a direct conversation.
     *
     * @param id          null for a club chat nobody has opened yet; {@code GET /chat/clubs/{clubId}} creates it
     * @param title       the club's name, or the other person's name
     * @param otherUserId the other person of a direct conversation
     * @param canSend     false when muted, blocked, or no longer sharing a club
     * @param reason      why canSend is false: MUTED, BLOCKED, NOT_CONNECTED
     */
    public record Conversation(UUID id, ConversationKind kind, UUID clubId, String title, String imageUrl,
                               UUID otherUserId, @JsonInclude(JsonInclude.Include.NON_NULL) Message lastMessage,
                               LocalDateTime lastMessageAt, long unread, boolean canSend,
                               @JsonInclude(JsonInclude.Include.NON_NULL) String reason,
                               @JsonInclude(JsonInclude.Include.NON_NULL) LocalDateTime mutedUntil,
                               boolean canModerate) {
    }

    public record BlockedUser(UUID userId, String name, String photoUrl, LocalDateTime blockedAt) {
    }

    public record Mute(UUID userId, String name, LocalDateTime mutedUntil, LocalDateTime mutedAt) {
    }

    /** For admins: a reported message with where it was written. */
    public record Report(UUID id, ChatReportStatus status, String reason, UUID reporterId, String reporterName,
                         Message message, ConversationKind kind, UUID clubId, String clubName,
                         LocalDateTime createdAt, LocalDateTime resolvedAt) {
    }

    /** What the live stream sends: "message" (new or deleted; see {@link Message#deleted()}). */
    public record Event(String type, UUID conversationId, Message message) {
    }
}
