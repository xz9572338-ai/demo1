CREATE TABLE applications (
 id BIGINT NOT NULL AUTO_INCREMENT, enterprise_id BIGINT NOT NULL, public_id CHAR(36) NOT NULL,
 app_id VARCHAR(64) NOT NULL, name VARCHAR(100) NOT NULL, purpose VARCHAR(500) NOT NULL,
 status VARCHAR(16) NOT NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY(id), CONSTRAINT uk_applications_enterprise_id UNIQUE(enterprise_id),
 CONSTRAINT uk_applications_public_id UNIQUE(public_id), CONSTRAINT uk_applications_app_id UNIQUE(app_id),
 CONSTRAINT fk_applications_enterprise_id FOREIGN KEY(enterprise_id) REFERENCES enterprises(id),
 CONSTRAINT chk_applications_status CHECK(status IN ('ACTIVE')));
CREATE TABLE application_credentials (
 id BIGINT NOT NULL AUTO_INCREMENT, application_id BIGINT NOT NULL, environment VARCHAR(16) NOT NULL,
 secret_ciphertext TEXT NOT NULL, secret_iv VARCHAR(64) NOT NULL, key_id VARCHAR(64) NOT NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(id), CONSTRAINT uk_application_credentials_environment UNIQUE(application_id,environment),
 CONSTRAINT fk_application_credentials_application_id FOREIGN KEY(application_id) REFERENCES applications(id),
 CONSTRAINT chk_application_credentials_environment CHECK(environment IN ('SANDBOX')));
