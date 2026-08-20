package com.company.openplatform.permission.application;

import com.company.openplatform.permission.domain.PermissionCode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ApprovedPermissionScope {
    private final JdbcTemplate jdbc;
    public ApprovedPermissionScope(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public String resolve(long applicationId, PermissionCode code) {
        List<String> values = jdbc.query("""
                select internal_customer_scope from application_permissions
                where application_id=? and permission_code=? and status='APPROVED'
                """, (row, number) -> row.getString(1), applicationId, code.name());
        if (values.size() != 1 || values.getFirst() == null || values.getFirst().isBlank()) return null;
        return values.getFirst().trim();
    }
}
