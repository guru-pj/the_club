package com.theclub.notificationservice.dto;

import com.theclub.notificationservice.model.EmailOutbox;
import com.theclub.notificationservice.model.EmailStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Admin view of an outbox row (bodies and attachments are deliberately left out).
 */
public record EmailOutboxResponse(UUID id, String recipient, String subject, String template, EmailStatus status,
                                  int attempts, LocalDateTime nextAttemptAt, String lastError, String provider,
                                  String providerMessageId, LocalDateTime createdAt, LocalDateTime sentAt) {

    public static EmailOutboxResponse from(EmailOutbox e) {
        return new EmailOutboxResponse(e.getId(), e.getRecipient(), e.getSubject(), e.getTemplate(), e.getStatus(),
                e.getAttempts(), e.getNextAttemptAt(), e.getLastError(), e.getProvider(), e.getProviderMessageId(),
                e.getCreatedAt(), e.getSentAt());
    }
}
