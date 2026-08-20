ALTER TABLE registration_applications
    ADD COLUMN rejection_reason VARCHAR(500) NULL,
    ADD COLUMN reviewed_by VARCHAR(100) NULL,
    ADD COLUMN reviewed_by_checker VARCHAR(100) NULL,
    ADD COLUMN reviewed_at DATETIME(6) NULL;
