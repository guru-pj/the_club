-- Production-grade Strava integration: durable webhook queue, encrypted tokens, sync bookkeeping, richer activity data.
-- Portable between PostgreSQL 15 and H2 (PostgreSQL mode) so the same file runs in tests.

-- Tokens are now stored AES-GCM encrypted ("v1:" + base64) and cleared when access is revoked.
ALTER TABLE strava_connections ALTER COLUMN access_token SET DATA TYPE VARCHAR(512);
ALTER TABLE strava_connections ALTER COLUMN refresh_token SET DATA TYPE VARCHAR(512);
ALTER TABLE strava_connections ALTER COLUMN access_token DROP NOT NULL;
ALTER TABLE strava_connections ALTER COLUMN refresh_token DROP NOT NULL;
ALTER TABLE strava_connections ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL;
ALTER TABLE strava_connections ADD COLUMN include_private BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE strava_connections ADD COLUMN last_synced_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE strava_connections ADD COLUMN sync_started_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE strava_connections ADD COLUMN revoked_at TIMESTAMP(6) WITH TIME ZONE;

ALTER TABLE activities ADD COLUMN elapsed_time INTEGER;
ALTER TABLE activities ADD COLUMN total_elevation_gain DOUBLE PRECISION;
ALTER TABLE activities ADD COLUMN average_heartrate DOUBLE PRECISION;
ALTER TABLE activities ADD COLUMN device_name VARCHAR(255);
ALTER TABLE activities ADD COLUMN is_private BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE activities ADD COLUMN start_time_utc TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE activities ADD COLUMN time_zone VARCHAR(64);

-- Webhook events are persisted on receipt and processed by a worker with retries (survives restarts and crashes).
-- The unique key absorbs Strava's re-deliveries of the same event.
CREATE TABLE strava_webhook_events (
    id              CHAR(36)                    NOT NULL,
    object_type     VARCHAR(20)                 NOT NULL,
    object_id       BIGINT                      NOT NULL,
    aspect_type     VARCHAR(20)                 NOT NULL,
    owner_id        BIGINT                      NOT NULL,
    updates         VARCHAR(2000),
    event_time      BIGINT                      NOT NULL,
    subscription_id BIGINT,
    status          VARCHAR(20)                 NOT NULL,
    attempts        INTEGER                     NOT NULL,
    next_attempt_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    lease_until     TIMESTAMP(6) WITH TIME ZONE,
    last_error      VARCHAR(1000),
    received_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    processed_at    TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_strava_webhook_events PRIMARY KEY (id),
    CONSTRAINT uk_strava_webhook_event UNIQUE (object_type, object_id, aspect_type, event_time)
);

CREATE INDEX idx_strava_webhook_due ON strava_webhook_events (status, next_attempt_at);
