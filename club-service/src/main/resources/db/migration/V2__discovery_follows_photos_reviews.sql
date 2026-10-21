-- User journey: location-based discovery, following separate from subscribing, club photos, reviews and ratings.

-- Map location (for "nearby") and whether the club offers subscriptions (membership) and with what benefits
ALTER TABLE clubs ADD COLUMN latitude DOUBLE;
ALTER TABLE clubs ADD COLUMN longitude DOUBLE;
ALTER TABLE clubs ADD COLUMN address VARCHAR(255);
ALTER TABLE clubs ADD COLUMN subscriptions_enabled BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE clubs ADD COLUMN subscription_benefits VARCHAR(1000);

CREATE INDEX idx_clubs_location ON clubs (latitude, longitude);

-- Followers get the club's posts and event announcements without becoming members
CREATE TABLE club_follows (
    id          CHAR(36)    NOT NULL,
    club_id     CHAR(36)    NOT NULL,
    user_id     CHAR(36)    NOT NULL,
    followed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_club_follows PRIMARY KEY (id),
    CONSTRAINT uk_follow_club_user UNIQUE (club_id, user_id),
    CONSTRAINT fk_follows_club FOREIGN KEY (club_id) REFERENCES clubs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_follow_user_id ON club_follows (user_id);

CREATE TABLE club_photos (
    id          CHAR(36)     NOT NULL,
    club_id     CHAR(36)     NOT NULL,
    url         VARCHAR(500) NOT NULL,
    caption     VARCHAR(200),
    uploaded_by CHAR(36)     NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_club_photos PRIMARY KEY (id),
    CONSTRAINT fk_photos_club FOREIGN KEY (club_id) REFERENCES clubs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_photo_club_id ON club_photos (club_id);

-- One review per person per club; the owner may reply
CREATE TABLE club_reviews (
    id             CHAR(36)      NOT NULL,
    club_id        CHAR(36)      NOT NULL,
    user_id        CHAR(36)      NOT NULL,
    rating         INT           NOT NULL,
    comment        VARCHAR(2000),
    owner_reply    VARCHAR(1000),
    owner_reply_at DATETIME(6),
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    CONSTRAINT pk_club_reviews PRIMARY KEY (id),
    CONSTRAINT uk_review_club_user UNIQUE (club_id, user_id),
    CONSTRAINT fk_reviews_club FOREIGN KEY (club_id) REFERENCES clubs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
