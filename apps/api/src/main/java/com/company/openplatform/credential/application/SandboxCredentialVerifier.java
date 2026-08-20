package com.company.openplatform.credential.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SandboxCredentialVerifier {
    private final JdbcTemplate jdbc;
    private final byte[] key;
    private final String keyId;

    public SandboxCredentialVerifier(JdbcTemplate jdbc,
            @Value("${open-platform.security.app-secret-key}") String encodedKey,
            @Value("${open-platform.security.app-secret-key-id}") String keyId) {
        this.jdbc = jdbc;
        this.key = Base64.getDecoder().decode(encodedKey);
        this.keyId = keyId == null ? "" : keyId.trim();
        if (key.length != 32 || this.keyId.isEmpty()) throw new IllegalStateException("invalid app credential key configuration");
    }

    public Material find(String appId) {
        List<Encrypted> rows = jdbc.query("""
                select a.id,a.enterprise_id,a.status,c.environment,c.secret_ciphertext,c.secret_iv,c.key_id,
                       exists(select 1 from accounts x where x.enterprise_id=a.enterprise_id and x.status='APPROVED')
                from applications a join application_credentials c on c.application_id=a.id
                where a.app_id=? and c.environment='SANDBOX'
                """, (row, number) -> new Encrypted(row.getLong(1), row.getLong(2), row.getString(3), row.getString(4),
                        row.getString(5), row.getString(6), row.getString(7), row.getBoolean(8)), appId);
        if (rows.size() != 1) return null;
        Encrypted row = rows.getFirst();
        return new Material(row.applicationId(), row.enterpriseId(), row.applicationStatus(), row.environment(),
                row.enterpriseApproved(), decrypt(row));
    }

    private byte[] decrypt(Encrypted row) {
        if (!keyId.equals(row.keyId())) throw new CredentialUnavailableException();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, Base64.getDecoder().decode(row.iv())));
            cipher.updateAAD(("application:" + row.applicationId() + ":SANDBOX:" + row.keyId()).getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(Base64.getDecoder().decode(row.ciphertext()));
        } catch (AEADBadTagException exception) {
            throw new CredentialUnavailableException();
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new CredentialUnavailableException();
        }
    }

    public void performDummyCrypto() {
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,new byte[12]));
            cipher.updateAAD("unknown:SANDBOX:dummy".getBytes(StandardCharsets.UTF_8));
            cipher.doFinal(new byte[32]);
        } catch (GeneralSecurityException exception) { throw new CredentialUnavailableException(); }
    }

    public static final class Material {
        private final long applicationId, enterpriseId; private final String applicationStatus, environment;
        private final boolean enterpriseApproved; private byte[] secret;
        public Material(long applicationId,long enterpriseId,String applicationStatus,String environment,boolean enterpriseApproved,byte[] secret){this.applicationId=applicationId;this.enterpriseId=enterpriseId;this.applicationStatus=applicationStatus;this.environment=environment;this.enterpriseApproved=enterpriseApproved;this.secret=secret;}
        public long applicationId(){return applicationId;} public long enterpriseId(){return enterpriseId;}
        public String applicationStatus(){return applicationStatus;} public String environment(){return environment;}
        public boolean enterpriseApproved(){return enterpriseApproved;}
        public byte[] takeSecret(){byte[] value=secret;secret=new byte[0];return value;}
    }
    private record Encrypted(long applicationId, long enterpriseId, String applicationStatus, String environment,
                             String ciphertext, String iv, String keyId, boolean enterpriseApproved) {}
    public static final class CredentialUnavailableException extends RuntimeException {}
}
