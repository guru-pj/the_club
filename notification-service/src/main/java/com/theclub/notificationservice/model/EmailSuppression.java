package com.theclub.notificationservice.model;

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

/**
 * An address that hard-bounced. Sending to it again hurts the domain's sender reputation, so it is skipped.
 */
@Entity
@Table(name = "email_suppression", uniqueConstraints = @UniqueConstraint(name = "uk_email_suppression_email", columnNames = "email"))
@Getter
@NoArgsConstructor
public class EmailSuppression {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public EmailSuppression(String email, String reason, LocalDateTime createdAt) {
        this.id = UuidGenerator.generate();
        this.email = email;
        this.reason = reason.length() <= 500 ? reason : reason.substring(0, 500);
        this.createdAt = createdAt;
    }
}
