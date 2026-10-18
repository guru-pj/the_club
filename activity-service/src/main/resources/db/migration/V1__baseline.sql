-- activity-service baseline schema (PostgreSQL 15). Enums are stored as VARCHAR so new values need no ALTER.
-- user_id references user-service, so it carries no foreign key.

CREATE TABLE activities (
    id          CHAR(36)         NOT NULL,
    user_id     CHAR(36)         NOT NULL,
    title       VARCHAR(100)     NOT NULL,
    type        VARCHAR(30)      NOT NULL,
    distance    DOUBLE PRECISION NOT NULL,
    duration    INTEGER          NOT NULL,
    calories    INTEGER,
    start_time  TIMESTAMP(6)     NOT NULL,
    end_time    TIMESTAMP(6)     NOT NULL,
    source      VARCHAR(20)      NOT NULL,
    external_id VARCHAR(64),
    created_at  TIMESTAMP(6)     NOT NULL,
    CONSTRAINT pk_activities PRIMARY KEY (id),
    CONSTRAINT uk_activity_source_external UNIQUE (source, external_id)
);

CREATE INDEX idx_activity_user ON activities (user_id);
CREATE INDEX idx_activity_user_start ON activities (user_id, start_time);

CREATE TABLE strava_connections (
    id            CHAR(36)                    NOT NULL,
    user_id       CHAR(36)                    NOT NULL,
    athlete_id    BIGINT                      NOT NULL,
    access_token  VARCHAR(255)                NOT NULL,
    refresh_token VARCHAR(255)                NOT NULL,
    expires_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    scope         VARCHAR(255),
    created_at    TIMESTAMP(6)                NOT NULL,
    updated_at    TIMESTAMP(6)                NOT NULL,
    CONSTRAINT pk_strava_connections PRIMARY KEY (id),
    CONSTRAINT uk_strava_connections_user UNIQUE (user_id),
    CONSTRAINT uk_strava_connections_athlete UNIQUE (athlete_id)
);
