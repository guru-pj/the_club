package com.theclub.clubservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/** A photo in the club's gallery, uploaded by its owner. */
@Entity
@Table(name = "club_photos", indexes = @Index(name = "idx_photo_club_id", columnList = "club_id"))
@Getter
@NoArgsConstructor
public class ClubPhoto {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "club_id", length = 36, nullable = false)
    private UUID clubId;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(length = 200)
    private String caption;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uploaded_by", length = 36, nullable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ClubPhoto(UUID clubId, String url, String caption, UUID uploadedBy) {
        this.id = UuidGenerator.generate();
        this.clubId = clubId;
        this.url = url;
        this.caption = caption;
        this.uploadedBy = uploadedBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
