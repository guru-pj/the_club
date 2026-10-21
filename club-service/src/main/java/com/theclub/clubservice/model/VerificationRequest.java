package com.theclub.clubservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "owner_verification_requests", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_status", columnList = "status")})
@Getter
@Setter
@NoArgsConstructor
public class VerificationRequest {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_id", length = 36, nullable = false)
    private UUID userId;

    @Column(name = "club_name", nullable = false, length = 200)
    private String clubName;

    @Enumerated(EnumType.STRING)
    @Column(name = "club_type", length = 50)
    private ClubType clubType;

    @Column(length = 100)
    private String city;

    @Convert(converter = JsonConverters.StringMap.class)
    @Column(name = "social_links", columnDefinition = "TEXT")
    private Map<String, String> socialLinks = new LinkedHashMap<>();

    @Convert(converter = JsonConverters.StringList.class)
    @Column(name = "proof_files", columnDefinition = "TEXT")
    private List<String> proofFiles = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(length = 255)
    private String address;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(length = 500)
    private String website;

    /** The club created when the application was approved. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "club_id", length = 36)
    private UUID clubId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationStatus status = VerificationStatus.PENDING;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "reviewed_by", length = 36)
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public VerificationRequest(UUID userId, String clubName) {
        this.id = UuidGenerator.generate();
        this.userId = userId;
        this.clubName = clubName;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
