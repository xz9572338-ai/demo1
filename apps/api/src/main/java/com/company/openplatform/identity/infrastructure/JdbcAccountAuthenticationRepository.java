package com.company.openplatform.identity.infrastructure;

import com.company.openplatform.identity.domain.AccountAuthenticationRepository;
import com.company.openplatform.identity.domain.MobileProtector;
import com.company.openplatform.identity.domain.RegistrationStatus;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAccountAuthenticationRepository implements AccountAuthenticationRepository {
    private final JdbcTemplate jdbc;
    private final MobileProtector mobileProtector;

    public JdbcAccountAuthenticationRepository(JdbcTemplate jdbc, MobileProtector mobileProtector) {
        this.jdbc = jdbc;
        this.mobileProtector = mobileProtector;
    }

    @Override
    public Optional<AccountAuthentication> findByLogin(String login) {
        String trimmed = login.trim();
        if (trimmed.matches("1[3-9]\\d{9}")) {
            return query("contact_mobile_fingerprint", mobileProtector.protect(trimmed).fingerprint());
        }
        return query("normalized_username", trimmed.toLowerCase(Locale.ROOT));
    }

    @Override
    public Optional<AccountAuthentication> findByPublicId(String publicId) {
        return query("public_id", publicId);
    }

    private Optional<AccountAuthentication> query(String column, String value) {
        List<AccountAuthentication> results = jdbc.query("SELECT public_id, password_hash, status FROM accounts WHERE "
                        + column + " = ?", (rs, row) -> new AccountAuthentication(rs.getString("public_id"),
                        rs.getString("password_hash"), RegistrationStatus.valueOf(rs.getString("status"))), value);
        return results.stream().findFirst();
    }
}
