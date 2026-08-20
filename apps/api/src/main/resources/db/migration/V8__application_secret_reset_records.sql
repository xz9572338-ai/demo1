CREATE TABLE application_secret_reset_records (
 id BIGINT NOT NULL AUTO_INCREMENT, application_id BIGINT NOT NULL, environment VARCHAR(16) NOT NULL,
 reason VARCHAR(500) NOT NULL, operated_by VARCHAR(100) NOT NULL, checked_by VARCHAR(100) NOT NULL,
 evidence VARCHAR(500) NOT NULL, request_id VARCHAR(100) NOT NULL, created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(id), CONSTRAINT fk_secret_reset_application_id FOREIGN KEY(application_id) REFERENCES applications(id),
 CONSTRAINT uk_secret_reset_request_id UNIQUE(request_id),
 CONSTRAINT chk_secret_reset_reviewers CHECK(operated_by <> checked_by),
 CONSTRAINT chk_secret_reset_environment CHECK(environment IN ('SANDBOX')),
 INDEX idx_secret_reset_application_created(application_id,created_at));
