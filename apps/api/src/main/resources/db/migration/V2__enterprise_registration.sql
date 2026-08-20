CREATE TABLE enterprises (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_enterprises_public_id UNIQUE (public_id)
);

CREATE TABLE accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    enterprise_id BIGINT NOT NULL,
    public_id CHAR(36) NOT NULL,
    username VARCHAR(64) NOT NULL,
    normalized_username VARCHAR(64) NOT NULL,
    contact_name VARCHAR(100) NOT NULL,
    contact_mobile_ciphertext TEXT NOT NULL,
    contact_mobile_key_id VARCHAR(64) NOT NULL,
    contact_mobile_fingerprint CHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_accounts_public_id UNIQUE (public_id),
    CONSTRAINT uk_accounts_normalized_username UNIQUE (normalized_username),
    CONSTRAINT fk_accounts_enterprise_id FOREIGN KEY (enterprise_id) REFERENCES enterprises (id),
    INDEX idx_accounts_enterprise_id (enterprise_id)
);

CREATE TABLE registration_applications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    enterprise_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    public_id CHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_registration_applications_public_id UNIQUE (public_id),
    CONSTRAINT uk_registration_applications_account_id UNIQUE (account_id),
    CONSTRAINT fk_registration_applications_enterprise_id FOREIGN KEY (enterprise_id) REFERENCES enterprises (id),
    CONSTRAINT fk_registration_applications_account_id FOREIGN KEY (account_id) REFERENCES accounts (id),
    INDEX idx_registration_applications_enterprise_id (enterprise_id),
    INDEX idx_registration_applications_status_submitted_at (status, submitted_at)
);
