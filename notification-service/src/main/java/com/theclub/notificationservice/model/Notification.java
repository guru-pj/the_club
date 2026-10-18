package com.theclub.notificationservice.model;

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

@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notifications_user_id", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    public static final int MAX_TYPE_LENGTH = 50;
    public static final int MAX_MESSAGE_LENGTH = 255;

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_id", length = 36, nullable = false)
    private UUID userId;

    @Column(nullable = false, length = MAX_TYPE_LENGTH)
    private String type;

    @Column(nullable = false, length = MAX_MESSAGE_LENGTH)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Notification(UUID userId, String type, String message, LocalDateTime createdAt) {
        this.id = UuidGenerator.generate();
        this.userId = userId;
        this.type = truncate(type, MAX_TYPE_LENGTH);
        this.message = truncate(message, MAX_MESSAGE_LENGTH);
        this.createdAt = createdAt;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
