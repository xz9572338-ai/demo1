ALTER TABLE registration_applications
    ADD CONSTRAINT ck_registration_status CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    ADD CONSTRAINT ck_registration_review_reason CHECK (
        (status = 'REJECTED' AND rejection_reason IS NOT NULL AND CHAR_LENGTH(TRIM(rejection_reason)) > 0)
        OR (status <> 'REJECTED' AND rejection_reason IS NULL)
    ),
    ADD CONSTRAINT ck_registration_review_audit CHECK (
        (status = 'PENDING_REVIEW' AND reviewed_by IS NULL AND reviewed_by_checker IS NULL AND reviewed_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED')
            AND reviewed_by IS NOT NULL AND CHAR_LENGTH(TRIM(reviewed_by)) > 0
            AND reviewed_by_checker IS NOT NULL AND CHAR_LENGTH(TRIM(reviewed_by_checker)) > 0
            AND TRIM(reviewed_by) <> TRIM(reviewed_by_checker)
            AND reviewed_at IS NOT NULL)
    );
