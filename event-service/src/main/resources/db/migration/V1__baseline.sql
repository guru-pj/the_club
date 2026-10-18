-- event-service baseline schema (MySQL 8). Enums are stored as VARCHAR so new values need no ALTER.
-- Club and user ids reference other services, so they carry no foreign keys.

CREATE TABLE events (
    id                     CHAR(36)      NOT NULL,
    club_id                CHAR(36)      NOT NULL,
    created_by             CHAR(36)      NOT NULL,
    title                  VARCHAR(150)  NOT NULL,
    description            TEXT,
    location               VARCHAR(255),
    start_time             DATETIME(6)   NOT NULL,
    registration_opens_at  DATETIME(6)   NOT NULL,
    registration_closes_at DATETIME(6),
    capacity               INT,
    price                  DECIMAL(10,2) NOT NULL,
    currency               VARCHAR(3)    NOT NULL,
    points                 INT           NOT NULL,
    status                 VARCHAR(20)   NOT NULL,
    created_at             DATETIME(6)   NOT NULL,
    updated_at             DATETIME(6)   NOT NULL,
    version                BIGINT        NOT NULL,
    CONSTRAINT pk_events PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_events_club_id ON events (club_id);

CREATE TABLE event_registrations (
    id            CHAR(36)    NOT NULL,
    event_id      CHAR(36)    NOT NULL,
    user_id       CHAR(36)    NOT NULL,
    status        VARCHAR(20) NOT NULL,
    ticket_code   VARCHAR(32),
    registered_at DATETIME(6) NOT NULL,
    confirmed_at  DATETIME(6),
    checked_in_at DATETIME(6),
    checked_in_by CHAR(36),
    version       BIGINT      NOT NULL,
    CONSTRAINT pk_event_registrations PRIMARY KEY (id),
    CONSTRAINT uk_registration_event_user UNIQUE (event_id, user_id),
    CONSTRAINT uk_registration_ticket_code UNIQUE (ticket_code),
    CONSTRAINT fk_registrations_event FOREIGN KEY (event_id) REFERENCES events (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_registration_user_id ON event_registrations (user_id);

CREATE TABLE payments (
    id                  CHAR(36)      NOT NULL,
    registration_id     CHAR(36)      NOT NULL,
    user_id             CHAR(36)      NOT NULL,
    event_id            CHAR(36)      NOT NULL,
    amount              DECIMAL(10,2) NOT NULL,
    currency            VARCHAR(3)    NOT NULL,
    provider            VARCHAR(20)   NOT NULL,
    provider_order_id   VARCHAR(100)  NOT NULL,
    provider_payment_id VARCHAR(100),
    status              VARCHAR(20)   NOT NULL,
    refund_id           VARCHAR(100),
    refund_reason       VARCHAR(255),
    refunded_at         DATETIME(6),
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uk_payment_provider_order_id UNIQUE (provider_order_id),
    CONSTRAINT fk_payments_registration FOREIGN KEY (registration_id) REFERENCES event_registrations (id),
    CONSTRAINT fk_payments_event FOREIGN KEY (event_id) REFERENCES events (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_payment_registration_id ON payments (registration_id);
CREATE INDEX idx_payment_event_id ON payments (event_id);
CREATE INDEX idx_payment_status_created_at ON payments (status, created_at);

CREATE TABLE payment_webhook_events (
    event_id    VARCHAR(100) NOT NULL,
    event_type  VARCHAR(50)  NOT NULL,
    received_at DATETIME(6)  NOT NULL,
    CONSTRAINT pk_payment_webhook_events PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE points_ledger (
    id         CHAR(36)    NOT NULL,
    user_id    CHAR(36)    NOT NULL,
    club_id    CHAR(36)    NOT NULL,
    event_id   CHAR(36)    NOT NULL,
    points     INT         NOT NULL,
    reason     VARCHAR(30) NOT NULL,
    awarded_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_points_ledger PRIMARY KEY (id),
    CONSTRAINT uk_points_user_event UNIQUE (user_id, event_id),
    CONSTRAINT fk_points_event FOREIGN KEY (event_id) REFERENCES events (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_points_awarded_at ON points_ledger (awarded_at);
CREATE INDEX idx_points_club_id ON points_ledger (club_id);
