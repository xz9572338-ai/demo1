UPDATE registration_applications
SET rejection_reason = '历史驳回记录，请联系企业微信或邮件确认详情',
    reviewed_by = '历史数据迁移', reviewed_by_checker = '历史数据复核', reviewed_at = updated_at
WHERE status = 'REJECTED' AND rejection_reason IS NULL;

UPDATE registration_applications
SET reviewed_by = '历史数据迁移', reviewed_by_checker = '历史数据复核', reviewed_at = updated_at
WHERE status = 'APPROVED' AND reviewed_at IS NULL;
