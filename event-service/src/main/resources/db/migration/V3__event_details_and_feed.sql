-- User journey steps 6–8: an event image, rules and instructions, and an optional "subscribers only" requirement.
ALTER TABLE events ADD COLUMN image_url VARCHAR(500);
ALTER TABLE events ADD COLUMN rules TEXT;
ALTER TABLE events ADD COLUMN subscribers_only BOOLEAN DEFAULT FALSE NOT NULL;

-- Until now only club members could register for any event: existing events keep that rule
UPDATE events SET subscribers_only = TRUE;

-- The upcoming-events feed: scheduled events of a set of clubs, soonest first
CREATE INDEX idx_events_status_start ON events (status, start_time);
