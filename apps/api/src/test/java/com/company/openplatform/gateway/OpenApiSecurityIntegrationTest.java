package com.company.openplatform.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.openplatform.application.application.ApplicationService;
import com.company.openplatform.gateway.security.OpenApiPrincipal;
import com.company.openplatform.permission.domain.PermissionCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(OpenApiSecurityIntegrationTest.ProbeController.class)
@TestPropertySource(properties={
        "open-platform.security.phone-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "open-platform.security.phone-key-id=test-v1",
        "open-platform.security.app-secret-key=BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
        "open-platform.security.app-secret-key-id=app-test-v1",
        "open-platform.security.open-api-dummy-hmac-key=REREREREREREREREREREREREREREREREREREREREREQ=",
        "open-platform.security.login.identifier-hmac-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
class OpenApiSecurityIntegrationTest {
    @Container @ServiceConnection static final MySQLContainer MYSQL=new MySQLContainer("mysql:8.4.7");
    @Container @ServiceConnection(name="redis") static final GenericContainer<?> REDIS=new GenericContainer<>("redis:8.4.0-alpine").withExposedPorts(6379);
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder passwords; @Autowired ApplicationService applications;
    private String appId, secret; private long internalApplicationId;

    @BeforeEach void setup(){
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        jdbc.update("delete from application_permissions");jdbc.update("delete from permission_requests");jdbc.update("delete from application_secret_reset_records");jdbc.update("delete from application_credentials");jdbc.update("delete from applications");jdbc.update("delete from registration_applications");jdbc.update("delete from accounts");jdbc.update("delete from enterprises");
        Instant now=Instant.now();String enterprise=UUID.randomUUID().toString();jdbc.update("insert into enterprises(public_id,name,created_at,updated_at) values(?,?,?,?)",enterprise,"安全测试企业",now,now);Long enterpriseId=jdbc.queryForObject("select id from enterprises where public_id=?",Long.class,enterprise);
        String account=UUID.randomUUID().toString();jdbc.update("insert into accounts(enterprise_id,public_id,username,normalized_username,contact_name,contact_mobile_ciphertext,contact_mobile_key_id,contact_mobile_fingerprint,password_hash,status,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,'APPROVED',?,?)",enterpriseId,account,"gateway","gateway","联系人","cipher","test",UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-",""),passwords.encode("password password"),now,now);
        var created=applications.create(account,"安全入口","集成测试");appId=created.application().appId();secret=created.appSecret();internalApplicationId=jdbc.queryForObject("select id from applications where app_id=?",Long.class,appId);
        approve(PermissionCode.CUSTOMER_BASE_READ,"customer-base");approve(PermissionCode.ORDER_LIST_READ,"orders-list");approve(PermissionCode.ORDER_DETAIL_READ,"orders-detail");
    }

    @Test void authenticatesWithEndpointSpecificScopeAndRejectsReplay()throws Exception{
        long timestamp=Instant.now().getEpochSecond();String nonce="nonce_1234567890abcdef";
        mvc.perform(signed("/sandbox/v1/orders",timestamp,nonce,secret,appId)).andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("orders-list")).andExpect(jsonPath("$.permission").value("ORDER_LIST_READ"));
        mvc.perform(signed("/sandbox/v1/orders",timestamp,nonce,secret,appId)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("NONCE_REPLAYED"));
        mvc.perform(signed("/sandbox/v1/orders/ORDER_1",timestamp,"nonce_1234567890abcdeg",secret,appId)).andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("orders-detail"));
    }

    @Test void invalidAndUnknownApplicationsHaveSameExternalFailure()throws Exception{
        long now=Instant.now().getEpochSecond();
        var bad=mvc.perform(signed("/sandbox/v1/orders",now,"nonce_1234567890abcdef","wrong-secret",appId)).andExpect(status().isUnauthorized()).andReturn().getResponse();
        var unknown=mvc.perform(signed("/sandbox/v1/orders",now,"nonce_1234567890abcdef","wrong-secret","app_"+"A".repeat(32))).andExpect(status().isUnauthorized()).andReturn().getResponse();
        assertThat(unknown.getContentAsString()).contains("SIGNATURE_INVALID");assertThat(bad.getContentAsString().replaceAll("req_[0-9a-f-]+","req_ID")).isEqualTo(unknown.getContentAsString().replaceAll("req_[0-9a-f-]+","req_ID"));
        assertThat(redis.keys("openapi:nonce:*")).isEmpty();
    }

    @Test void enforcesTimestampPermissionAndAtomicRateLimit()throws Exception{
        long now=Instant.now().getEpochSecond();
        mvc.perform(signed("/sandbox/v1/orders",now-301,"nonce_1234567890abcdef",secret,appId)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TIMESTAMP_EXPIRED"));
        jdbc.update("update application_permissions set status='REJECTED',internal_customer_scope=null,rejection_reason='denied' where application_id=? and permission_code='CUSTOMER_BASE_READ'",internalApplicationId);
        mvc.perform(signed("/sandbox/v1/customers/00000000-0000-0000-0000-000000000001",now,"nonce_1234567890abcdeg",secret,appId)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        for(int index=0;index<20;index++)mvc.perform(signed("/sandbox/v1/orders",Instant.now().getEpochSecond(),String.format("nonce_%016d",index),secret,appId)).andExpect(status().isOk());
        mvc.perform(signed("/sandbox/v1/orders",Instant.now().getEpochSecond(),"nonce_9999999999999999",secret,appId)).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","1")).andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test void rejectsMalformedCookieAndUnknownPathsWithoutConsoleFallback()throws Exception{
        mvc.perform(get("/sandbox/v1/orders").cookie(new jakarta.servlet.http.Cookie("SESSION","fake"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/sandbox/v1/unknown").header("X-App-ID",appId).header("X-Timestamp",Instant.now().getEpochSecond()).header("X-Nonce","nonce_1234567890abcdef").header("X-Signature","0".repeat(64))).andExpect(status().isBadRequest());
        mvc.perform(get("/openapi/v1/orders")).andExpect(status().isForbidden());
    }

    @Test void rejectsHeaderAndTargetBoundariesBeforeAuthentication()throws Exception{
        mvc.perform(get("/sandbox/v1/orders").header("X-App-ID",appId,appId).header("X-Timestamp",Instant.now().getEpochSecond()).header("X-Nonce","nonce_1234567890abcdef").header("X-Signature","0".repeat(64))).andExpect(status().isBadRequest());
        mvc.perform(get("/sandbox/v1/orders?x="+"a".repeat(4097))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/sandbox/v1")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test void canonicalizesQueryAndClaimsConcurrentNonceExactlyOnce()throws Exception{
        long now=Instant.now().getEpochSecond();
        mvc.perform(signedQuery("/sandbox/v1/orders","pageSize=20&page=1","page=1&pageSize=20",now,"nonce_query_12345678",secret,appId)).andExpect(status().isOk());
        String nonce="nonce_concurrent_1234";
        var first=CompletableFuture.supplyAsync(()->performStatus(now,nonce));var second=CompletableFuture.supplyAsync(()->performStatus(now,nonce));
        assertThat(java.util.List.of(first.join(),second.join())).containsExactlyInAnyOrder(200,401);
    }

    @Test @ExtendWith(OutputCaptureExtension.class)
    void rejectsBodiesAndLogsNoCredentials(CapturedOutput output)throws Exception{
        String nonce="nonce_secret_123456";
        mvc.perform(get("/sandbox/v1/orders").content("unsigned-body")).andExpect(status().isBadRequest());
        mvc.perform(signed("/sandbox/v1/orders",Instant.now().getEpochSecond(),nonce,"wrong-secret",appId)).andExpect(status().isUnauthorized());
        assertThat(output.getOut()).doesNotContain(secret,"wrong-secret",nonce,"unsigned-body");
        assertThat(output.getOut().split("open_api_security result=SIGNATURE_INVALID",-1)).hasSize(2);
    }

    @Test void permissionAndCredentialFailuresUseUnavailableContract()throws Exception{
        jdbc.execute("rename table application_permissions to application_permissions_unavailable");
        try{mvc.perform(signed("/sandbox/v1/orders",Instant.now().getEpochSecond(),"nonce_permission_fail",secret,appId)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));}
        finally{jdbc.execute("rename table application_permissions_unavailable to application_permissions");}
        jdbc.update("update application_credentials set secret_ciphertext='invalid' where application_id=?",internalApplicationId);
        mvc.perform(signed("/sandbox/v1/orders",Instant.now().getEpochSecond(),"nonce_decrypt_fail_1",secret,appId)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test void outerRequestIdBoundaryUsesOpenApiUnavailableContract()throws Exception{
        jdbc.execute("rename table application_credentials to application_credentials_unavailable");
        try{mvc.perform(signed("/sandbox/v1/orders",Instant.now().getEpochSecond(),"nonce_failure_123456",secret,appId)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE")).andExpect(header().exists("X-Request-ID"));}
        finally{jdbc.execute("rename table application_credentials_unavailable to application_credentials");}
    }

    private void approve(PermissionCode code,String scope){Instant now=Instant.now();String id=UUID.randomUUID().toString();jdbc.update("insert into permission_requests(public_id,application_id,permission_code,reason,status,internal_customer_scope,operated_by,checked_by,reviewed_at,created_at,updated_at) values(?,?,?,'test','APPROVED',?,'operator','checker',?,?,?)",id,internalApplicationId,code.name(),scope,now,now,now);Long request=jdbc.queryForObject("select id from permission_requests where public_id=?",Long.class,id);jdbc.update("insert into application_permissions(application_id,permission_code,status,current_request_id,internal_customer_scope,updated_at) values(?,?,'APPROVED',?,?,?)",internalApplicationId,code.name(),request,scope,now);}
    private MockHttpServletRequestBuilder signed(String path,long timestamp,String nonce,String key,String id)throws Exception{String canonical=String.join("\n","GET",contractPath(path),"",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[0])),id,Long.toString(timestamp),nonce);Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return get(path).header("X-App-ID",id).header("X-Timestamp",timestamp).header("X-Nonce",nonce).header("X-Signature",HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));}
    private MockHttpServletRequestBuilder signedQuery(String path,String rawQuery,String canonicalQuery,long timestamp,String nonce,String key,String id)throws Exception{String canonical=String.join("\n","GET",contractPath(path),canonicalQuery,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[0])),id,Long.toString(timestamp),nonce);Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return get(path+"?"+rawQuery).header("X-App-ID",id).header("X-Timestamp",timestamp).header("X-Nonce",nonce).header("X-Signature",HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))));}
    private String contractPath(String path){return path.substring("/sandbox/v1".length());}
    private int performStatus(long timestamp,String nonce){try{return mvc.perform(signed("/sandbox/v1/orders",timestamp,nonce,secret,appId)).andReturn().getResponse().getStatus();}catch(Exception failure){throw new RuntimeException(failure);}}

    @RestController static class ProbeController {
        @GetMapping("/sandbox/v1/orders") ResponseEntity<Map<String,String>> orders(Authentication auth){return response(auth);}
        @GetMapping("/sandbox/v1/orders/{id}") ResponseEntity<Map<String,String>> order(@PathVariable String id,Authentication auth){return response(auth);}
        @GetMapping("/sandbox/v1/customers/{id}") ResponseEntity<Map<String,String>> customer(@PathVariable String id,Authentication auth){return response(auth);}
        private ResponseEntity<Map<String,String>> response(Authentication auth){OpenApiPrincipal p=(OpenApiPrincipal)auth.getPrincipal();return ResponseEntity.ok(Map.of("scope",p.internalCustomerScope(),"permission",p.permissionCode().name()));}
    }
}
