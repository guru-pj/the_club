-- Profile setup (user journey step 2): an approximate location for nearby clubs and events, and when the user
-- finished setting up their profile, so the app stops showing the setup screen.
ALTER TABLE users ADD COLUMN latitude DOUBLE;
ALTER TABLE users ADD COLUMN longitude DOUBLE;
ALTER TABLE users ADD COLUMN profile_completed_at DATETIME(6);

-- Profile setup is for new accounts: people who already use the app aren't sent through it
UPDATE users SET profile_completed_at = created_at;
