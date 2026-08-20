-- 在受控客户端中绑定 :application_public_id、:result、:reason、:operator、:checker。
START TRANSACTION;
SELECT r.account_id FROM registration_applications r
WHERE r.public_id = :application_public_id AND r.status = 'PENDING_REVIEW'
FOR UPDATE;

UPDATE registration_applications r
SET r.status = :result,
    r.rejection_reason = CASE WHEN :result = 'REJECTED' THEN :reason ELSE NULL END,
    r.reviewed_by = :operator, r.reviewed_by_checker = :checker,
    r.reviewed_at = UTC_TIMESTAMP(6), r.updated_at = UTC_TIMESTAMP(6)
WHERE r.public_id = :application_public_id AND r.status = 'PENDING_REVIEW'
  AND :result IN ('APPROVED', 'REJECTED')
  AND (:result <> 'REJECTED' OR NULLIF(TRIM(:reason), '') IS NOT NULL)
  AND NULLIF(TRIM(:operator), '') IS NOT NULL
  AND NULLIF(TRIM(:checker), '') IS NOT NULL
  AND TRIM(:operator) <> TRIM(:checker);
SELECT ROW_COUNT() AS application_rows;

UPDATE accounts a JOIN registration_applications r ON r.account_id = a.id
SET a.status = :result, a.updated_at = UTC_TIMESTAMP(6)
WHERE r.public_id = :application_public_id AND r.status = :result AND a.status = 'PENDING_REVIEW';
SELECT ROW_COUNT() AS account_rows;

-- 操作工具必须分别断言 application_rows = 1 且 account_rows = 1；否则 ROLLBACK，均满足才 COMMIT。
