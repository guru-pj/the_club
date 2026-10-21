package com.theclub.clubservice.model;

import com.theclub.common.util.UuidGenerator;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
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
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "club_posts", indexes = @Index(name = "idx_posts_club_created", columnList = "club_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
public class Post {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "club_id", length = 36, nullable = false)
    private UUID clubId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "author_id", length = 36, nullable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostType type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "club_post_photos", joinColumns = @JoinColumn(name = "post_id"))
    @OrderColumn(name = "position")
    @Column(name = "url", length = 500, nullable = false)
    private List<String> photoUrls = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "event_id", length = 36)
    private UUID eventId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "activity_id", length = 36)
    private UUID activityId;

    @Column(name = "activity_title", length = 100)
    private String activityTitle;

    @Column(name = "activity_type", length = 30)
    private String activityType;

    @Column(name = "activity_distance_km")
    private Double activityDistanceKm;

    @Column(name = "activity_duration_seconds")
    private Integer activityDurationSeconds;

    @Column(name = "activity_start_time")
    private LocalDateTime activityStartTime;

    /** A reshare of another club's post (always the original, never a reshare of a reshare). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "shared_post_id", length = 36)
    private UUID sharedPostId;

    /** A reshared event, with what's shown of it; the event itself lives in event-service. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "shared_event_id", length = 36)
    private UUID sharedEventId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "shared_event_club_id", length = 36)
    private UUID sharedEventClubId;

    @Column(name = "shared_event_title", length = 150)
    private String sharedEventTitle;

    @Column(name = "shared_event_start_time")
    private LocalDateTime sharedEventStartTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Post(UUID clubId, UUID authorId, PostType type, String title, String content) {
        this.id = UuidGenerator.generate();
        this.clubId = clubId;
        this.authorId = authorId;
        this.type = type;
        this.title = title;
        this.content = content;
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
