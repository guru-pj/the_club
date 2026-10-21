package com.theclub.clubservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "chat_reports",
        uniqueConstraints = @UniqueConstraint(name = "uk_report_message_reporter", columnNames = {"message_id", "reporter_id"}))
@Getter
@NoArgsConstructor
public class ChatReport {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "message_id", length = 36, nullable = false)
    private UUID messageId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "reporter_id", length = 36, nullable = false)
    private UUID reporterId;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatReportStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "resolved_by", length = 36)
    private UUID resolvedBy;

    public ChatReport(UUID messageId, UUID reporterId, String reason, LocalDateTime createdAt) {
        this.id = UuidGenerator.generate();
        this.messageId = messageId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.status = ChatReportStatus.OPEN;
        this.createdAt = createdAt;
    }

    public void resolve(ChatReportStatus outcome, UUID by, LocalDateTime now) {
        status = outcome;
        resolvedBy = by;
        resolvedAt = now;
    }
}
