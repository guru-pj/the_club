-- user-service baseline schema (MySQL 8). Enums are stored as VARCHAR so new values need no ALTER.

CREATE TABLE users (
    id              CHAR(36)     NOT NULL,
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(254) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    bio             VARCHAR(255),
    city            VARCHAR(100),
    profile_pic_url VARCHAR(500),
    is_active       BOOLEAN      NOT NULL,
    email_verified  BOOLEAN      NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_roles (
    user_id CHAR(36)    NOT NULL,
    role    VARCHAR(20) NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_tokens (
    id         CHAR(36)    NOT NULL,
    user_id    CHAR(36)    NOT NULL,
    purpose    VARCHAR(30) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_user_tokens PRIMARY KEY (id),
    CONSTRAINT uk_user_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_user_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_user_tokens_user_purpose ON user_tokens (user_id, purpose);
