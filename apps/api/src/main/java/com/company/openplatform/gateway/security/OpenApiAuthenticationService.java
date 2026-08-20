package com.company.openplatform.gateway.security;

import com.company.openplatform.credential.application.SandboxCredentialVerifier;
import com.company.openplatform.permission.application.ApprovedPermissionScope;
import com.company.openplatform.permission.domain.PermissionCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;

@Service
class OpenApiAuthenticationService {
    private final SandboxCredentialVerifier credentials; private final ApprovedPermissionScope permissions;
    private final OpenApiRedisGuard redis; private final Clock clock; private final byte[] dummyKey;
    OpenApiAuthenticationService(SandboxCredentialVerifier credentials, ApprovedPermissionScope permissions,
            OpenApiRedisGuard redis, Clock clock, @Value("${open-platform.security.open-api-dummy-hmac-key}") String dummy) {
        this.credentials=credentials;this.permissions=permissions;this.redis=redis;this.clock=clock;
        this.dummyKey=java.util.Base64.getDecoder().decode(dummy);if(dummyKey.length<32)throw new IllegalStateException("dummy HMAC key must be at least 256 bit");
    }
    OpenApiPrincipal authenticate(String appId,String timestamp,String nonce,String signature,String canonical,
                                  PermissionCode permission,String endpoint,String requestId){
        SandboxCredentialVerifier.Material material;
        try{material=credentials.find(appId);}catch(SandboxCredentialVerifier.CredentialUnavailableException failure){throw new OpenApiFailure(503,"SERVICE_UNAVAILABLE",true);}
        if(material==null)credentials.performDummyCrypto();
        byte[] key=material==null?dummyKey:material.takeSecret();
        byte[] expected=hmac(key,canonical);if(material!=null)java.util.Arrays.fill(key,(byte)0);
        if(!MessageDigest.isEqual(expected,HexFormat.of().parseHex(signature))||material==null)throw new OpenApiFailure(401,"SIGNATURE_INVALID",false);
        long signedAt;try{signedAt=Long.parseLong(timestamp);}catch(NumberFormatException invalid){throw new OpenApiFailure(400,"VALIDATION_FAILED",false);}
        long now=clock.instant().getEpochSecond();if(Math.abs(now-signedAt)>300)throw new OpenApiFailure(401,"TIMESTAMP_EXPIRED",true);
        long ttl=Math.max(1,Math.min(600,signedAt+300-now));
        if(!redis.claimNonce("SANDBOX",material.applicationId(),nonce,ttl))throw new OpenApiFailure(401,"NONCE_REPLAYED",true);
        if(!material.enterpriseApproved()||!"ACTIVE".equals(material.applicationStatus()))throw new OpenApiFailure(403,"APPLICATION_INACTIVE",false);
        if(!"SANDBOX".equals(material.environment()))throw new OpenApiFailure(403,"ENVIRONMENT_MISMATCH",false);
        String scope;try{scope=permissions.resolve(material.applicationId(),permission);}catch(DataAccessException failure){throw new OpenApiFailure(503,"SERVICE_UNAVAILABLE",true);}
        if(scope==null)throw new OpenApiFailure(403,"PERMISSION_DENIED",false);
        redis.consume("SANDBOX",material.applicationId(),endpoint,clock.millis());
        return new OpenApiPrincipal(material.enterpriseId(),material.applicationId(),"SANDBOX",permission,scope,requestId,requestId);
    }
    private byte[] hmac(byte[] key,String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
}
