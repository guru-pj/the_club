-- auth-service baseline schema (MySQL 8). Only SHA-256 hashes of refresh tokens are stored.

CREATE TABLE auth_tokens (
    id                 CHAR(36)    NOT NULL,
    user_id            CHAR(36)    NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    expiry             DATETIME(6) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    CONSTRAINT pk_auth_tokens PRIMARY KEY (id),
    CONSTRAINT uk_auth_tokens_refresh_token_hash UNIQUE (refresh_token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_auth_tokens_user_id ON auth_tokens (user_id);

CREATE TABLE login_attempts (
    id                CHAR(36)     NOT NULL,
    email             VARCHAR(254) NOT NULL,
    failed_count      INT          NOT NULL,
    window_started_at DATETIME(6),
    locked_until      DATETIME(6),
    CONSTRAINT pk_login_attempts PRIMARY KEY (id),
    CONSTRAINT uk_login_attempts_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
