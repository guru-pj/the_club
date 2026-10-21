package com.theclub.userservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    @Column(length = 255)
    private String bio;

    @Column(length = 100)
    private String city;

    @Column(name = "profile_pic_url", length = 500)
    private String profilePicUrl;

    /** Approximate (rounded to ~100 m) and only shown to the user themself; used to find nearby clubs and events. */
    @Column
    private Double latitude;

    @Column
    private Double longitude;

    /** Set when the user finishes (or skips) the profile setup screen after their first login. */
    @Column(name = "profile_completed_at")
    private LocalDateTime profileCompletedAt;

    /** Signed up as a club owner: the app leads them to the club application. Grants nothing by itself. */
    @Column(name = "club_owner_signup", nullable = false)
    private boolean clubOwnerSignup;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public User(String name, String email, String passwordHash, Set<Role> roles) {
        this.id = UuidGenerator.generate();
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.roles = EnumSet.copyOf(roles);
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
