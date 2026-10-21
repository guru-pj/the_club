-- Club social: subscribers post too, likes and comments, reshares into another club's feed, and chat
-- (one group chat per club for its subscribers, plus direct messages between people who share a club).

-- A reshare points at the original post, or at an event (with a snapshot, because events live in event-service)
ALTER TABLE club_posts ADD COLUMN shared_post_id CHAR(36);
ALTER TABLE club_posts ADD COLUMN shared_event_id CHAR(36);
ALTER TABLE club_posts ADD COLUMN shared_event_club_id CHAR(36);
ALTER TABLE club_posts ADD COLUMN shared_event_title VARCHAR(150);
ALTER TABLE club_posts ADD COLUMN shared_event_start_time DATETIME(6);

CREATE INDEX idx_posts_shared_post ON club_posts (shared_post_id);

CREATE TABLE post_likes (
    id         CHAR(36)    NOT NULL,
    post_id    CHAR(36)    NOT NULL,
    user_id    CHAR(36)    NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_post_likes PRIMARY KEY (id),
    CONSTRAINT uk_like_post_user UNIQUE (post_id, user_id),
    CONSTRAINT fk_likes_post FOREIGN KEY (post_id) REFERENCES club_posts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE post_comments (
    id         CHAR(36)      NOT NULL,
    post_id    CHAR(36)      NOT NULL,
    author_id  CHAR(36)      NOT NULL,
    content    VARCHAR(1000) NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    CONSTRAINT pk_post_comments PRIMARY KEY (id),
    CONSTRAINT fk_comments_post FOREIGN KEY (post_id) REFERENCES club_posts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_comments_post_created ON post_comments (post_id, created_at);

-- GROUP: one per club (club_id set). DIRECT: one per pair of people (user_low < user_high).
CREATE TABLE chat_conversations (
    id              CHAR(36)    NOT NULL,
    kind            VARCHAR(10) NOT NULL,
    club_id         CHAR(36),
    user_low        CHAR(36),
    user_high       CHAR(36),
    created_at      DATETIME(6) NOT NULL,
    last_message_at DATETIME(6),
    CONSTRAINT pk_chat_conversations PRIMARY KEY (id),
    CONSTRAINT uk_chat_club UNIQUE (club_id),
    CONSTRAINT uk_chat_pair UNIQUE (user_low, user_high),
    CONSTRAINT fk_chat_club FOREIGN KEY (club_id) REFERENCES clubs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_chat_user_high ON chat_conversations (user_high);

-- Message IDs are UUIDv7, so they sort by time. A deleted message is hidden from everyone, but its row and content
-- stay so an admin can still review reports about it.
CREATE TABLE chat_messages (
    id              CHAR(36)      NOT NULL,
    conversation_id CHAR(36)      NOT NULL,
    sender_id       CHAR(36)      NOT NULL,
    body            VARCHAR(2000),
    photo_url       VARCHAR(500),
    created_at      DATETIME(6)   NOT NULL,
    deleted_at      DATETIME(6),
    deleted_by      CHAR(36),
    CONSTRAINT pk_chat_messages PRIMARY KEY (id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_messages_conversation ON chat_messages (conversation_id, id);
CREATE INDEX idx_messages_created ON chat_messages (created_at);
CREATE INDEX idx_messages_deleted ON chat_messages (deleted_at);

-- Unread counts: messages after the last time the person opened the conversation
CREATE TABLE chat_reads (
    id              CHAR(36)    NOT NULL,
    conversation_id CHAR(36)    NOT NULL,
    user_id         CHAR(36)    NOT NULL,
    last_read_at    DATETIME(6) NOT NULL,
    CONSTRAINT pk_chat_reads PRIMARY KEY (id),
    CONSTRAINT uk_read_conversation_user UNIQUE (conversation_id, user_id),
    CONSTRAINT fk_reads_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- A block stops direct messages both ways and hides the blocked person's group messages from the blocker
CREATE TABLE chat_blocks (
    id         CHAR(36)    NOT NULL,
    blocker_id CHAR(36)    NOT NULL,
    blocked_id CHAR(36)    NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_chat_blocks PRIMARY KEY (id),
    CONSTRAINT uk_block_pair UNIQUE (blocker_id, blocked_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_block_blocked ON chat_blocks (blocked_id);

-- Reported messages wait for an admin
CREATE TABLE chat_reports (
    id          CHAR(36)    NOT NULL,
    message_id  CHAR(36)    NOT NULL,
    reporter_id CHAR(36)    NOT NULL,
    reason      VARCHAR(500),
    status      VARCHAR(20) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    resolved_at DATETIME(6),
    resolved_by CHAR(36),
    CONSTRAINT pk_chat_reports PRIMARY KEY (id),
    CONSTRAINT uk_report_message_reporter UNIQUE (message_id, reporter_id),
    CONSTRAINT fk_reports_message FOREIGN KEY (message_id) REFERENCES chat_messages (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_reports_status ON chat_reports (status, created_at);

-- The club owner can mute someone in the club chat: they still read it but can't write (muted_until NULL = until unmuted)
CREATE TABLE club_chat_mutes (
    id          CHAR(36)    NOT NULL,
    club_id     CHAR(36)    NOT NULL,
    user_id     CHAR(36)    NOT NULL,
    muted_by    CHAR(36)    NOT NULL,
    muted_until DATETIME(6),
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_club_chat_mutes PRIMARY KEY (id),
    CONSTRAINT uk_mute_club_user UNIQUE (club_id, user_id),
    CONSTRAINT fk_mutes_club FOREIGN KEY (club_id) REFERENCES clubs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
