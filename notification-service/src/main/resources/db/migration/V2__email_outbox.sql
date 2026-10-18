-- Transactional outbox for emails: rows are written in the same transaction as the in-app notification and
-- delivered by EmailDispatcher with retries, so an email is never lost when the mail provider is down.

CREATE TABLE email_outbox (
    id                  CHAR(36)      NOT NULL,
    recipient           VARCHAR(254)  NOT NULL,
    subject             VARCHAR(500)  NOT NULL,
    template            VARCHAR(80),
    html_body           MEDIUMTEXT    NOT NULL,
    text_body           MEDIUMTEXT    NOT NULL,
    attachments         MEDIUMTEXT,
    status              VARCHAR(20)   NOT NULL,
    attempts            INT           NOT NULL,
    next_attempt_at     DATETIME(6)   NOT NULL,
    claimed_at          DATETIME(6),
    last_error          VARCHAR(1000),
    provider            VARCHAR(20),
    provider_message_id VARCHAR(255),
    created_at          DATETIME(6)   NOT NULL,
    sent_at             DATETIME(6),
    CONSTRAINT pk_email_outbox PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_email_outbox_status_next ON email_outbox (status, next_attempt_at);

-- Addresses that hard-bounced (reported by the ZeptoMail webhook); the dispatcher never emails them again.
CREATE TABLE email_suppression (
    id         CHAR(36)      NOT NULL,
    email      VARCHAR(254)  NOT NULL,
    reason     VARCHAR(500)  NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    CONSTRAINT pk_email_suppression PRIMARY KEY (id),
    CONSTRAINT uk_email_suppression_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
