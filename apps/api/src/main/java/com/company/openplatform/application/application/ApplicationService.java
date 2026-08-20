package com.company.openplatform.application.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {
    private static final int APP_ID_ATTEMPTS = 3;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final byte[] key;
    private final String keyId;
    private final SecureRandom random = new SecureRandom();

    public ApplicationService(JdbcTemplate jdbc, Clock clock,
            @Value("${open-platform.security.app-secret-key}") String key,
            @Value("${open-platform.security.app-secret-key-id}") String keyId) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.key = Base64.getDecoder().decode(key);
        this.keyId = keyId == null ? "" : keyId.trim();
        if (this.key.length != 32) throw new IllegalStateException("app secret key must be 256 bit");
        if (this.keyId.isEmpty() || this.keyId.length() > 64) throw new IllegalStateException("app secret key id is required and must not exceed 64 characters");
    }

    public record View(String applicationId, String name, String purpose, String appId,
                       String environment, String status, Instant createdAt, Instant updatedAt) {}
    public record Created(View application, String appSecret) {}
    private record AccountScope(long enterpriseId, String status) {}
    private record CredentialSnapshot(long id, long version) {}

    @Transactional
    public Created create(String accountId, String name, String purpose) {
        AccountScope scope = approvedScope(accountId);
        Instant now = clock.instant();
        String publicId = null;
        String appId = null;
        boolean inserted = false;
        for (int attempt = 0; attempt < APP_ID_ATTEMPTS && !inserted; attempt++) {
            publicId = UUID.randomUUID().toString();
            appId = "app_" + token(24);
            try {
                jdbc.update("insert into applications(enterprise_id,public_id,app_id,name,purpose,status,created_at,updated_at) values(?,?,?,?,?,'ACTIVE',?,?)",
                        scope.enterpriseId(), publicId, appId, name.trim(), purpose.trim(), now, now);
                inserted = true;
            } catch (DuplicateKeyException exception) {
                String cause = String.valueOf(exception.getMostSpecificCause().getMessage());
                if (cause.contains("uk_applications_enterprise_id")) throw new ApplicationAlreadyExistsException();
                if (attempt + 1 == APP_ID_ATTEMPTS) throw new IllegalStateException("unable to allocate unique AppID", exception);
            }
        }
        if (!inserted) throw new IllegalStateException("unable to create application");

        Long id = jdbc.queryForObject("select id from applications where public_id=?", Long.class, publicId);
        String secret = token(32);
        byte[] iv = new byte[12];
        random.nextBytes(iv);
        jdbc.update("insert into application_credentials(application_id,environment,secret_ciphertext,secret_iv,key_id,created_at,updated_at) values(?,'SANDBOX',?,?,?,?,?)",
                id, encrypt(secret, iv, id), Base64.getEncoder().encodeToString(iv), keyId, now, now);
        return new Created(new View(publicId, name.trim(), purpose.trim(), appId, "SANDBOX", "ACTIVE", now, now), secret);
    }

    public List<View> list(String accountId) {
        AccountScope scope = approvedScope(accountId);
        return jdbc.query("select public_id,name,purpose,app_id,status,created_at,updated_at from applications where enterprise_id=?",
                (row, number) -> new View(row.getString(1), row.getString(2), row.getString(3), row.getString(4),
                        "SANDBOX", row.getString(5), row.getTimestamp(6).toInstant(), row.getTimestamp(7).toInstant()),
                scope.enterpriseId());
    }

    @Transactional
    public String resetSandboxSecret(String applicationId, String reason, String operator, String checker,
                                     String evidence, String requestId) {
        String normalizedReason = metadata(reason, 500, "reason");
        String normalizedOperator = metadata(operator, 100, "operator");
        String normalizedChecker = metadata(checker, 100, "checker");
        String normalizedEvidence = metadata(evidence, 500, "evidence");
        String normalizedRequestId = metadata(requestId, 100, "requestId");
        if (normalizedOperator.equalsIgnoreCase(normalizedChecker))
            throw new IllegalArgumentException("operator and checker must differ");

        List<CredentialSnapshot> observed = jdbc.query("select c.id,c.version from application_credentials c join applications a on a.id=c.application_id where a.public_id=? and a.status='ACTIVE' and c.environment='SANDBOX'",
                (row, number) -> new CredentialSnapshot(row.getLong(1), row.getLong(2)), applicationId);
        if (observed.size() != 1) throw new IllegalStateException("expected exactly one active sandbox credential");
        List<Long> applications = jdbc.query("select id from applications where public_id=? and status='ACTIVE' for update",
                (row, number) -> row.getLong(1), applicationId);
        if (applications.size() != 1) throw new IllegalStateException("expected exactly one active application");
        Long id = applications.getFirst();
        Integer replay = jdbc.queryForObject("select count(*) from application_secret_reset_records where request_id=?",
                Integer.class, normalizedRequestId);
        if (replay != null && replay > 0) throw new IllegalStateException("reset request already processed");
        String secret = token(32);
        byte[] iv = new byte[12];
        random.nextBytes(iv);
        Instant now = clock.instant();
        int changed = jdbc.update("update application_credentials set secret_ciphertext=?,secret_iv=?,key_id=?,updated_at=?,version=version+1 where id=? and version=?",
                encrypt(secret, iv, id), Base64.getEncoder().encodeToString(iv), keyId, now,
                observed.getFirst().id(), observed.getFirst().version());
        if (changed != 1) throw new IllegalStateException("sandbox credential changed during reset");
        jdbc.update("insert into application_secret_reset_records(application_id,environment,reason,operated_by,checked_by,evidence,request_id,created_at) values(?,'SANDBOX',?,?,?,?,?,?)",
                id, normalizedReason, normalizedOperator, normalizedChecker, normalizedEvidence, normalizedRequestId, now);
        return secret;
    }

    private AccountScope approvedScope(String accountId) {
        List<AccountScope> scopes = jdbc.query("select enterprise_id,status from accounts where public_id=?",
                (row, number) -> new AccountScope(row.getLong(1), row.getString(2)), accountId);
        if (scopes.isEmpty()) throw new ApplicationAuthenticationRequiredException();
        AccountScope scope = scopes.getFirst();
        if (!"APPROVED".equals(scope.status())) throw new ApplicationAccessDeniedException();
        return scope;
    }

    private String token(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String encrypt(String value, byte[] iv, long applicationId) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(applicationId));
            return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("credential encryption failed", exception);
        }
    }

    private byte[] aad(long applicationId) {
        return ("application:" + applicationId + ":SANDBOX:" + keyId).getBytes(StandardCharsets.UTF_8);
    }

    private String metadata(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > maxLength)
            throw new IllegalArgumentException(field + " exceeds maximum length");
        return normalized;
    }
}
