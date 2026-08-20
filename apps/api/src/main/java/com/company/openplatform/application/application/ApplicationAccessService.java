package com.company.openplatform.application.application;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ApplicationAccessService {
    private final JdbcTemplate jdbc;
    public ApplicationAccessService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public OwnedApplication requireOwnedActive(String accountId, String applicationId) {
        List<OwnedApplication> rows = jdbc.query("select a.id,a.public_id from applications a join accounts ac on ac.enterprise_id=a.enterprise_id where ac.public_id=? and ac.status='APPROVED' and a.public_id=? and a.status='ACTIVE'",
                (rs, n) -> new OwnedApplication(rs.getLong(1), rs.getString(2)), accountId, applicationId);
        if (rows.size() != 1) throw new ApplicationAccessDeniedException();
        return rows.getFirst();
    }
    public record OwnedApplication(long id, String publicId) {}
}
