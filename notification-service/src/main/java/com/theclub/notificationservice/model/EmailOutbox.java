package com.theclub.notificationservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One rendered email waiting for (or done with) delivery. Bodies are stored fully rendered, so a retry sends
 * exactly what was originally composed.
 */
@Entity
@Table(name = "email_outbox", indexes = @Index(name = "idx_email_outbox_status_next", columnList = "status, next_attempt_at"))
@Getter
@Setter
@NoArgsConstructor
public class EmailOutbox {

    public static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 254)
    private String recipient;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(length = 80)
    private String template;

    @Column(name = "html_body", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String htmlBody;

    @Column(name = "text_body", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String textBody;

    /** JSON array of attachments (base64 content), or null. */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String attachments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(length = 20)
    private String provider;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public EmailOutbox(String recipient, String subject, String template, String htmlBody, String textBody,
                       String attachments, LocalDateTime now) {
        this.id = UuidGenerator.generate();
        this.recipient = recipient;
        this.subject = subject;
        this.template = template;
        this.htmlBody = htmlBody;
        this.textBody = textBody;
        this.attachments = attachments;
        this.status = EmailStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError == null || lastError.length() <= MAX_ERROR_LENGTH
                ? lastError : lastError.substring(0, MAX_ERROR_LENGTH);
    }
}
