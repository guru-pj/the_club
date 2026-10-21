-- Before per-club verification, an admin could approve an applicant without creating the club, and the new owner then
-- created it themselves. Link those approvals to the club the applicant created under the same name.
UPDATE owner_verification_requests SET club_id = (
    SELECT MIN(c.id) FROM clubs c
    WHERE c.owner_id = owner_verification_requests.user_id
      AND LOWER(c.name) = LOWER(owner_verification_requests.club_name))
WHERE status = 'APPROVED' AND club_id IS NULL;
