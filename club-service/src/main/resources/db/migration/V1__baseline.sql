-- club-service baseline schema (MySQL 8). Enums are stored as VARCHAR so new values need no ALTER.
-- User, event and activity ids reference other services, so they carry no foreign keys.

CREATE TABLE clubs (
    id          CHAR(36)     NOT NULL,
    name        VARCHAR(120) NOT NULL,
    description TEXT,
    city        VARCHAR(100),
    club_type   VARCHAR(50),
    logo_url    VARCHAR(500),
    owner_id    CHAR(36)     NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_clubs PRIMARY KEY (id),
    CONSTRAINT uk_clubs_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_clubs_owner_id ON clubs (owner_id);

CREATE TABLE club_memberships (
    id        CHAR(36)    NOT NULL,
    club_id   CHAR(36)    NOT NULL,
    user_id   CHAR(36)    NOT NULL,
    role      VARCHAR(20) NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_club_memberships PRIMARY KEY (id),
    CONSTRAINT uk_membership_club_user UNIQUE (club_id, user_id),
    CONSTRAINT fk_memberships_club FOREIGN KEY (club_id) REFERENCES clubs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_membership_user_id ON club_memberships (user_id);

CREATE TABLE club_posts (
    id                        CHAR(36)     NOT NULL,
    club_id                   CHAR(36)     NOT NULL,
    author_id                 CHAR(36)     NOT NULL,
    type                      VARCHAR(20)  NOT NULL,
    title                     VARCHAR(150) NOT NULL,
    content                   TEXT,
    event_id                  CHAR(36),
    activity_id               CHAR(36),
    activity_title            VARCHAR(100),
    activity_type             VARCHAR(30),
    activity_distance_km      DOUBLE,
    activity_duration_seconds INT,
    activity_start_time       DATETIME(6),
    created_at                DATETIME(6)  NOT NULL,
    updated_at                DATETIME(6)  NOT NULL,
    CONSTRAINT pk_club_posts PRIMARY KEY (id),
    CONSTRAINT fk_posts_club FOREIGN KEY (club_id) REFERENCES clubs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_posts_club_created ON club_posts (club_id, created_at);

CREATE TABLE club_post_photos (
    post_id  CHAR(36)     NOT NULL,
    position INT          NOT NULL,
    url      VARCHAR(500) NOT NULL,
    CONSTRAINT pk_club_post_photos PRIMARY KEY (post_id, position),
    CONSTRAINT fk_post_photos_post FOREIGN KEY (post_id) REFERENCES club_posts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE owner_verification_requests (
    id            CHAR(36)     NOT NULL,
    user_id       CHAR(36)     NOT NULL,
    club_name     VARCHAR(200) NOT NULL,
    club_type     VARCHAR(50),
    city          VARCHAR(100),
    social_links  TEXT,
    proof_files   TEXT,
    description   TEXT,
    contact_phone VARCHAR(20),
    status        VARCHAR(20)  NOT NULL,
    admin_notes   TEXT,
    reviewed_by   CHAR(36),
    reviewed_at   DATETIME(6),
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_owner_verification_requests PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_user_id ON owner_verification_requests (user_id);
CREATE INDEX idx_status ON owner_verification_requests (status);

CREATE TABLE owner_verification_audit (
    id         CHAR(36)    NOT NULL,
    request_id CHAR(36)    NOT NULL,
    actor_id   CHAR(36)    NOT NULL,
    action     VARCHAR(30) NOT NULL,
    note       TEXT,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_owner_verification_audit PRIMARY KEY (id),
    CONSTRAINT fk_audit_request FOREIGN KEY (request_id) REFERENCES owner_verification_requests (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_audit_request_id ON owner_verification_audit (request_id);
