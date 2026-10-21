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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "clubs", indexes = {@Index(name = "idx_clubs_owner_id", columnList = "owner_id"),
        @Index(name = "idx_clubs_location", columnList = "latitude, longitude")})
@Getter
@Setter
@NoArgsConstructor
public class Club {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "club_type", length = 50)
    private ClubType clubType;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    /** Map position for "nearby" discovery; both set or both null. */
    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(length = 255)
    private String address;

    /** Whether members can subscribe (join) at all; following is always possible. */
    @Column(name = "subscriptions_enabled", nullable = false)
    private boolean subscriptionsEnabled = true;

    /** What subscribing gives, shown before someone subscribes (e.g. "Members-only long runs"). */
    @Column(name = "subscription_benefits", length = 1000)
    private String subscriptionBenefits;

    /** Wide banner shown at the top of the club page. */
    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(length = 500)
    private String website;

    /** e.g. instagram, facebook, strava → URL */
    @Convert(converter = JsonConverters.StringMap.class)
    @Column(name = "social_links", columnDefinition = "TEXT")
    private Map<String, String> socialLinks = new LinkedHashMap<>();

    /** House rules members agree to (safety, etiquette, kit). */
    @Column(columnDefinition = "TEXT")
    private String rules;

    /** When an admin verified who runs the club; every club is created from an approved application. */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "owner_id", length = 36, nullable = false)
    private UUID ownerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Club(String name, String description, String city, ClubType clubType, String logoUrl, UUID ownerId) {
        this.id = UuidGenerator.generate();
        this.name = name;
        this.description = description;
        this.city = city;
        this.clubType = clubType;
        this.logoUrl = logoUrl;
        this.ownerId = ownerId;
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
