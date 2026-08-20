package com.company.openplatform.identity.infrastructure;

import com.company.openplatform.identity.domain.AccountAlreadyExistsException;
import com.company.openplatform.identity.domain.RegistrationRepository;
import com.company.openplatform.identity.domain.RegistrationStatus;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JpaRegistrationRepository implements RegistrationRepository {
    private final JdbcTemplate jdbc;

    public JpaRegistrationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RegistrationSaved save(RegistrationDraft draft) {
        try {
            long enterpriseId = insertEnterprise(draft);
            long accountId = insertAccount(draft, enterpriseId);
            String applicationId = UUID.randomUUID().toString();
            jdbc.update("""
                    INSERT INTO registration_applications
                    (enterprise_id, account_id, public_id, status, submitted_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, enterpriseId, accountId, applicationId, RegistrationStatus.PENDING_REVIEW.name(),
                    draft.submittedAt(), draft.submittedAt(), draft.submittedAt());
            return new RegistrationSaved(applicationId, RegistrationStatus.PENDING_REVIEW, draft.submittedAt());
        } catch (DuplicateKeyException exception) {
            if (accountExists(draft.normalizedUsername())) {
                throw new AccountAlreadyExistsException();
            }
            throw exception;
        }
    }

    private boolean accountExists(String normalizedUsername) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE normalized_username = ?", Integer.class, normalizedUsername);
        return count != null && count > 0;
    }

    private long insertEnterprise(RegistrationDraft draft) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO enterprises (public_id, name, created_at, updated_at) VALUES (?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, draft.enterpriseName());
            statement.setObject(3, draft.submittedAt());
            statement.setObject(4, draft.submittedAt());
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private long insertAccount(RegistrationDraft draft, long enterpriseId) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO accounts
                    (enterprise_id, public_id, username, normalized_username, contact_name,
                     contact_mobile_ciphertext, contact_mobile_key_id, contact_mobile_fingerprint,
                     password_hash, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, enterpriseId);
            statement.setString(2, UUID.randomUUID().toString());
            statement.setString(3, draft.username());
            statement.setString(4, draft.normalizedUsername());
            statement.setString(5, draft.contactName());
            statement.setString(6, draft.mobile().ciphertext());
            statement.setString(7, draft.mobile().keyId());
            statement.setString(8, draft.mobile().fingerprint());
            statement.setString(9, draft.passwordHash());
            statement.setString(10, RegistrationStatus.PENDING_REVIEW.name());
            statement.setObject(11, draft.submittedAt());
            statement.setObject(12, draft.submittedAt());
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }
}
