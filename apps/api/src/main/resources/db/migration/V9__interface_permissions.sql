CREATE TABLE permission_requests (
 id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, application_id BIGINT NOT NULL,
 permission_code VARCHAR(32) NOT NULL, reason VARCHAR(500) NOT NULL, status VARCHAR(20) NOT NULL,
 rejection_reason VARCHAR(500) NULL, internal_customer_scope VARCHAR(100) NULL,
 operated_by VARCHAR(100) NULL, checked_by VARCHAR(100) NULL, reviewed_at DATETIME(6) NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY(id), CONSTRAINT uk_permission_requests_public UNIQUE(public_id),
 CONSTRAINT fk_permission_requests_application FOREIGN KEY(application_id) REFERENCES applications(id),
 CONSTRAINT chk_permission_requests_code CHECK(permission_code IN ('CUSTOMER_BASE_READ','ORDER_LIST_READ','ORDER_DETAIL_READ')),
 CONSTRAINT chk_permission_requests_status CHECK(status IN ('PENDING_REVIEW','APPROVED','REJECTED')),
 CONSTRAINT chk_permission_requests_review CHECK((status='PENDING_REVIEW' AND rejection_reason IS NULL AND internal_customer_scope IS NULL AND operated_by IS NULL AND checked_by IS NULL AND reviewed_at IS NULL) OR (status='APPROVED' AND rejection_reason IS NULL AND CHAR_LENGTH(TRIM(internal_customer_scope))>0 AND CHAR_LENGTH(TRIM(operated_by))>0 AND CHAR_LENGTH(TRIM(checked_by))>0 AND reviewed_at IS NOT NULL AND LOWER(TRIM(operated_by))<>LOWER(TRIM(checked_by))) OR (status='REJECTED' AND CHAR_LENGTH(TRIM(rejection_reason))>0 AND internal_customer_scope IS NULL AND CHAR_LENGTH(TRIM(operated_by))>0 AND CHAR_LENGTH(TRIM(checked_by))>0 AND reviewed_at IS NOT NULL AND LOWER(TRIM(operated_by))<>LOWER(TRIM(checked_by)))));
CREATE INDEX idx_permission_requests_current ON permission_requests(application_id,permission_code,created_at);

CREATE TABLE application_permissions (
 id BIGINT NOT NULL AUTO_INCREMENT, application_id BIGINT NOT NULL, permission_code VARCHAR(32) NOT NULL,
 status VARCHAR(20) NOT NULL, current_request_id BIGINT NOT NULL, internal_customer_scope VARCHAR(100) NULL,
 rejection_reason VARCHAR(500) NULL, updated_at DATETIME(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(id), CONSTRAINT uk_application_permissions_code UNIQUE(application_id,permission_code),
 CONSTRAINT fk_application_permissions_application FOREIGN KEY(application_id) REFERENCES applications(id),
 CONSTRAINT fk_application_permissions_request FOREIGN KEY(current_request_id) REFERENCES permission_requests(id),
 CONSTRAINT chk_application_permissions_code CHECK(permission_code IN ('CUSTOMER_BASE_READ','ORDER_LIST_READ','ORDER_DETAIL_READ')),
 CONSTRAINT chk_application_permissions_status CHECK(status IN ('PENDING_REVIEW','APPROVED','REJECTED')),
 CONSTRAINT chk_application_permissions_result CHECK((status='PENDING_REVIEW' AND rejection_reason IS NULL AND internal_customer_scope IS NULL) OR (status='APPROVED' AND rejection_reason IS NULL AND CHAR_LENGTH(TRIM(internal_customer_scope))>0) OR (status='REJECTED' AND CHAR_LENGTH(TRIM(rejection_reason))>0 AND internal_customer_scope IS NULL)));
