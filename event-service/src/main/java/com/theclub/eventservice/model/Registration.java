package com.theclub.eventservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_registrations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_registration_event_user", columnNames = {"event_id", "user_id"}),
                @UniqueConstraint(name = "uk_registration_ticket_code", columnNames = "ticket_code")},
        indexes = @Index(name = "idx_registration_user_id", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class Registration {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "event_id", length = 36, nullable = false, updatable = false)
    private UUID eventId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus status;

    /** Secret encoded in the QR ticket; set when the registration is confirmed. */
    @Column(name = "ticket_code", length = 32)
    private String ticketCode;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "checked_in_by", length = 36)
    private UUID checkedInBy;

    @Version
    private long version;

    public Registration(UUID eventId, UUID userId) {
        this.id = UuidGenerator.generate();
        this.eventId = eventId;
        this.userId = userId;
    }

    public boolean hasTicket() {
        return status == RegistrationStatus.CONFIRMED || status == RegistrationStatus.CHECKED_IN;
    }
}
