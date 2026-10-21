-- People who sign up "as a club owner" go straight to the club application after their first login. Becoming an
-- owner still needs an admin to approve an application for each club; this only remembers what they signed up for.
ALTER TABLE users ADD COLUMN club_owner_signup BOOLEAN DEFAULT FALSE NOT NULL;
