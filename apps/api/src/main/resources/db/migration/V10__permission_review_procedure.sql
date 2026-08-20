DELIMITER $$
CREATE PROCEDURE review_permission_request(
  IN p_request_id CHAR(36), IN p_decision VARCHAR(20), IN p_customer_reason VARCHAR(500),
  IN p_customer_scope VARCHAR(100), IN p_operator VARCHAR(100), IN p_checker VARCHAR(100),
  IN p_reviewed_at DATETIME(6)
)
MODIFIES SQL DATA
BEGIN
  DECLARE request_rows INT DEFAULT 0;
  DECLARE permission_rows INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  START TRANSACTION;
  UPDATE permission_requests
     SET status=p_decision,
         rejection_reason=CASE WHEN p_decision='REJECTED' THEN TRIM(p_customer_reason) ELSE NULL END,
         internal_customer_scope=CASE WHEN p_decision='APPROVED' THEN TRIM(p_customer_scope) ELSE NULL END,
         operated_by=TRIM(p_operator), checked_by=TRIM(p_checker),
         reviewed_at=p_reviewed_at, updated_at=p_reviewed_at
   WHERE public_id=p_request_id AND status='PENDING_REVIEW'
     AND p_reviewed_at IS NOT NULL
     AND NULLIF(TRIM(p_operator),'') IS NOT NULL AND NULLIF(TRIM(p_checker),'') IS NOT NULL
     AND LOWER(TRIM(p_operator))<>LOWER(TRIM(p_checker))
     AND ((p_decision='APPROVED' AND NULLIF(TRIM(p_customer_scope),'') IS NOT NULL AND p_customer_reason IS NULL)
       OR (p_decision='REJECTED' AND NULLIF(TRIM(p_customer_reason),'') IS NOT NULL AND p_customer_scope IS NULL));
  SET request_rows=ROW_COUNT();
  IF request_rows<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='permission request review target must equal one'; END IF;
  UPDATE application_permissions p JOIN permission_requests r ON r.id=p.current_request_id
     SET p.status=r.status,p.internal_customer_scope=r.internal_customer_scope,
         p.rejection_reason=r.rejection_reason,p.updated_at=r.updated_at,p.version=p.version+1
   WHERE r.public_id=p_request_id AND p.status='PENDING_REVIEW' AND r.status IN ('APPROVED','REJECTED');
  SET permission_rows=ROW_COUNT();
  IF permission_rows<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='permission projection review target must equal one'; END IF;
  COMMIT;
  SELECT request_rows,permission_rows;
END$$
DELIMITER ;
