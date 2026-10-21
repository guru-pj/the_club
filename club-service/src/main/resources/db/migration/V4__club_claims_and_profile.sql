-- Club owner journey: every club is verified on its own (one application per club), applications carry the club's
-- contact and location details and private documents, and clubs get a fuller public profile.

-- What the applicant says about the club they claim
ALTER TABLE owner_verification_requests ADD COLUMN contact_email VARCHAR(255);
ALTER TABLE owner_verification_requests ADD COLUMN address VARCHAR(255);
ALTER TABLE owner_verification_requests ADD COLUMN latitude DOUBLE;
ALTER TABLE owner_verification_requests ADD COLUMN longitude DOUBLE;
ALTER TABLE owner_verification_requests ADD COLUMN website VARCHAR(500);
-- The club created when the application was approved
ALTER TABLE owner_verification_requests ADD COLUMN club_id CHAR(36);

UPDATE owner_verification_requests SET club_id = (
    SELECT MAX(a.note) FROM owner_verification_audit a
    WHERE a.request_id = owner_verification_requests.id AND a.action = 'CLUB_CREATED');

CREATE INDEX idx_verification_club ON owner_verification_requests (club_id);

-- Proof documents uploaded with an application. They're private: kept under private/ in the bucket, never served
-- by /media, and only shown to the applicant and admins.
CREATE TABLE owner_verification_documents (
    id           CHAR(36)     NOT NULL,
    request_id   CHAR(36)     NOT NULL,
    file_name    VARCHAR(200) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    storage_key  VARCHAR(500) NOT NULL,
    uploaded_by  CHAR(36)     NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    CONSTRAINT pk_owner_verification_documents PRIMARY KEY (id),
    CONSTRAINT fk_documents_request FOREIGN KEY (request_id) REFERENCES owner_verification_requests (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_documents_request ON owner_verification_documents (request_id);

-- The club's public profile
ALTER TABLE clubs ADD COLUMN cover_url VARCHAR(500);
ALTER TABLE clubs ADD COLUMN contact_email VARCHAR(255);
ALTER TABLE clubs ADD COLUMN contact_phone VARCHAR(30);
ALTER TABLE clubs ADD COLUMN website VARCHAR(500);
ALTER TABLE clubs ADD COLUMN social_links TEXT;
ALTER TABLE clubs ADD COLUMN rules TEXT;
-- When its ownership was verified; clubs created before per-club verification were set up by verified owners or
-- admins, so they count as verified
ALTER TABLE clubs ADD COLUMN verified_at DATETIME(6);
UPDATE clubs SET verified_at = created_at;
