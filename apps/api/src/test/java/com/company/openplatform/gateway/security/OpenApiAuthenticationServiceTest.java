package com.company.openplatform.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import org.springframework.dao.DataAccessResourceFailureException;

import com.company.openplatform.credential.application.SandboxCredentialVerifier;
import com.company.openplatform.permission.application.ApprovedPermissionScope;
import com.company.openplatform.permission.domain.PermissionCode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenApiAuthenticationServiceTest {
    private final SandboxCredentialVerifier credentials=mock(SandboxCredentialVerifier.class);
    private final ApprovedPermissionScope permissions=mock(ApprovedPermissionScope.class);
    private final OpenApiRedisGuard redis=mock(OpenApiRedisGuard.class);
    private final Clock clock=Clock.fixed(Instant.ofEpochSecond(2_000_000_000L),ZoneOffset.UTC);
    private OpenApiAuthenticationService service;
    @BeforeEach void setup(){service=new OpenApiAuthenticationService(credentials,permissions,redis,clock,Base64.getEncoder().encodeToString("D".repeat(32).getBytes(StandardCharsets.UTF_8)));}

    @Test void acceptsInclusiveTimestampEdgesAndBindsOnlyResolvedScope()throws Exception{
        when(credentials.find("app_"+"A".repeat(32))).thenAnswer(call->new SandboxCredentialVerifier.Material(7,8,"ACTIVE","SANDBOX",true,"secret".getBytes(StandardCharsets.UTF_8)));
        when(redis.claimNonce(anyString(),anyLong(),anyString(),anyLong())).thenReturn(true);when(permissions.resolve(7,PermissionCode.ORDER_LIST_READ)).thenReturn("orders-only");
        for(long timestamp:new long[]{1_999_999_700L,2_000_000_300L}){String canonical="canonical-"+timestamp;var result=service.authenticate("app_"+"A".repeat(32),Long.toString(timestamp),"nonce_1234567890abcdef",sign("secret",canonical),canonical,PermissionCode.ORDER_LIST_READ,"orders","req_test");assertThat(result.internalCustomerScope()).isEqualTo("orders-only");}
    }
    @Test void expiredAndInvalidSignaturesConsumeNoRedisState()throws Exception{
        when(credentials.find(anyString())).thenAnswer(call->new SandboxCredentialVerifier.Material(7,8,"ACTIVE","SANDBOX",true,"secret".getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(()->service.authenticate("app_"+"A".repeat(32),"1999999699","nonce_1234567890abcdef",sign("secret","canonical"),"canonical",PermissionCode.ORDER_LIST_READ,"orders","req")).isInstanceOf(OpenApiFailure.class).extracting("code").isEqualTo("TIMESTAMP_EXPIRED");
        assertThatThrownBy(()->service.authenticate("app_"+"A".repeat(32),"2000000301","nonce_1234567890abcdef",sign("secret","canonical"),"canonical",PermissionCode.ORDER_LIST_READ,"orders","req")).isInstanceOf(OpenApiFailure.class).extracting("code").isEqualTo("TIMESTAMP_EXPIRED");
        assertThatThrownBy(()->service.authenticate("app_"+"A".repeat(32),"2000000000","nonce_1234567890abcdef","0".repeat(64),"canonical",PermissionCode.ORDER_LIST_READ,"orders","req")).isInstanceOf(OpenApiFailure.class).extracting("code").isEqualTo("SIGNATURE_INVALID");
        verify(redis,never()).claimNonce(anyString(),anyLong(),anyString(),anyLong());
    }
    @Test void rejectsInactiveUnapprovedWrongEnvironmentAndBlankScope()throws Exception{
        when(redis.claimNonce(anyString(),anyLong(),anyString(),anyLong())).thenReturn(true);
        for(SandboxCredentialVerifier.Material material:java.util.List.of(
                new SandboxCredentialVerifier.Material(7,8,"DISABLED","SANDBOX",true,"secret".getBytes(StandardCharsets.UTF_8)),
                new SandboxCredentialVerifier.Material(7,8,"ACTIVE","SANDBOX",false,"secret".getBytes(StandardCharsets.UTF_8)),
                new SandboxCredentialVerifier.Material(7,8,"ACTIVE","PRODUCTION",true,"secret".getBytes(StandardCharsets.UTF_8)))){
            when(credentials.find(anyString())).thenReturn(material);
            assertThatThrownBy(()->service.authenticate("app_"+"A".repeat(32),"2000000000","nonce_1234567890abcdef",sign("secret","canonical"),"canonical",PermissionCode.ORDER_LIST_READ,"orders","req")).isInstanceOf(OpenApiFailure.class);
        }
        when(credentials.find(anyString())).thenReturn(new SandboxCredentialVerifier.Material(7,8,"ACTIVE","SANDBOX",true,"secret".getBytes(StandardCharsets.UTF_8)));when(permissions.resolve(7,PermissionCode.ORDER_LIST_READ)).thenReturn(null);
        assertThatThrownBy(()->service.authenticate("app_"+"A".repeat(32),"2000000000","nonce_1234567890abcdeg",sign("secret","canonical"),"canonical",PermissionCode.ORDER_LIST_READ,"orders","req")).isInstanceOf(OpenApiFailure.class).extracting("code").isEqualTo("PERMISSION_DENIED");
    }
    @Test void authorizationRejectionClaimsNonceButConsumesNoQuota()throws Exception{
        when(credentials.find(anyString())).thenReturn(new SandboxCredentialVerifier.Material(7,8,"ACTIVE","SANDBOX",true,"secret".getBytes(StandardCharsets.UTF_8)));
        when(redis.claimNonce(anyString(),anyLong(),anyString(),anyLong())).thenReturn(true);
        assertThatThrownBy(()->service.authenticate("app_"+"A".repeat(32),"2000000000","nonce_1234567890abcdef",sign("secret","canonical"),"canonical",PermissionCode.ORDER_LIST_READ,"orders","req")).isInstanceOf(OpenApiFailure.class).extracting("code").isEqualTo("PERMISSION_DENIED");
        verify(redis,times(1)).claimNonce(anyString(),anyLong(),anyString(),anyLong());verify(redis,never()).consume(anyString(),anyLong(),anyString(),anyLong());
    }
    @Test void permissionDependencyFailureIsUnavailable()throws Exception{
        when(credentials.find(anyString())).thenReturn(new SandboxCredentialVerifier.Material(7,8,"ACTIVE","SANDBOX",true,"secret".getBytes(StandardCharsets.UTF_8)));
        when(redis.claimNonce(anyString(),anyLong(),anyString(),anyLong())).thenReturn(true);when(permissions.resolve(anyLong(),any())).thenThrow(new DataAccessResourceFailureException("down"));
        assertThatThrownBy(()->service.authenticate("app_"+"A".repeat(32),"2000000000","nonce_1234567890abcdef",sign("secret","canonical"),"canonical",PermissionCode.ORDER_LIST_READ,"orders","req")).isInstanceOf(OpenApiFailure.class).extracting("code").isEqualTo("SERVICE_UNAVAILABLE");
    }
    private String sign(String secret,String value)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}
}
