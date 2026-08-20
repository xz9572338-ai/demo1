package com.company.openplatform.identity.infrastructure;

import com.company.openplatform.identity.application.OnboardingStatusQuery;
import com.company.openplatform.identity.domain.RegistrationStatus;
import java.util.Optional;
import com.company.openplatform.identity.domain.AuthenticationServiceUnavailableException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOnboardingStatusQuery implements OnboardingStatusQuery {
    private final JdbcTemplate jdbc;
    public JdbcOnboardingStatusQuery(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Optional<Result> findByAccountPublicId(String accountId) {
        try {
            return jdbc.query("""
                    SELECT a.status, r.status, r.submitted_at, COALESCE(r.reviewed_at, r.updated_at), r.rejection_reason
                    FROM accounts a JOIN registration_applications r ON r.account_id=a.id
                    WHERE a.public_id=?
                    """, (rs, row) -> new Result(RegistrationStatus.valueOf(rs.getString(1)),
                    RegistrationStatus.valueOf(rs.getString(2)), rs.getTimestamp(3).toInstant(),
                    rs.getTimestamp(4).toInstant(), rs.getString(5)), accountId).stream().findFirst();
        } catch (DataAccessException | IllegalArgumentException exception) {
            throw new AuthenticationServiceUnavailableException(exception);
        }
    }
}
