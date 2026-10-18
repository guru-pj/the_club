-- notification-service baseline schema (MySQL 8).

CREATE TABLE notifications (
    id         CHAR(36)     NOT NULL,
    user_id    CHAR(36)     NOT NULL,
    type       VARCHAR(50)  NOT NULL,
    message    VARCHAR(255) NOT NULL,
    is_read    BOOLEAN      NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_notifications_user_id ON notifications (user_id);
