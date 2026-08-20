package com.company.openplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.ObjectMapper;
import com.company.openplatform.application.application.ApplicationService;
import com.company.openplatform.application.application.ApplicationAccessDeniedException;
import com.company.openplatform.application.application.ApplicationAlreadyExistsException;
import com.company.openplatform.application.infrastructure.ApplicationSecretResetRunner;
import com.company.openplatform.permission.application.PermissionConflictException;
import com.company.openplatform.permission.application.PermissionService;
import com.company.openplatform.permission.domain.PermissionCode;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
        "open-platform.security.phone-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "open-platform.security.phone-key-id=test-v1",
        "open-platform.security.app-secret-key=BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
        "open-platform.security.app-secret-key-id=app-test-v1",
        "open-platform.security.login.identifier-hmac-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "open-platform.security.login.account-limit=5",
        "open-platform.security.login.ip-limit=20",
        "open-platform.security.login.window-seconds=900",
        "open-platform.security.login.lock-seconds=1",
        "server.servlet.session.cookie.secure=true"})
class ConsoleSessionIntegrationTest {
    @Container @ServiceConnection static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.7");
    @Container @ServiceConnection(name = "redis") static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8.4.0-alpine").withExposedPorts(6379);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper mapper;
    @Autowired StringRedisTemplate redis;
    @Autowired TransactionTemplate transactions;
    @Autowired ApplicationService applicationService;
    @Autowired PermissionService permissionService;

    @BeforeEach
    void clean() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        jdbc.update("delete from application_permissions"); jdbc.update("delete from permission_requests");
        jdbc.update("delete from application_secret_reset_records"); jdbc.update("delete from application_credentials"); jdbc.update("delete from applications");
        jdbc.update("delete from registration_applications"); jdbc.update("delete from accounts");
        jdbc.update("delete from enterprises");
    }

    @Test
    void permissionRequestsAreTenantScopedAtomicAndDefaultDenied() throws Exception {
        createAccount("permission_a", "correct horse battery staple", "APPROVED");
        createAccount("permission_b", "correct horse battery staple", "APPROVED");
        String accountA=jdbc.queryForObject("select public_id from accounts where normalized_username='permission_a'",String.class);
        String accountB=jdbc.queryForObject("select public_id from accounts where normalized_username='permission_b'",String.class);
        var appA=applicationService.create(accountA,"A 应用","权限申请").application();
        var appB=applicationService.create(accountB,"B 应用","隔离验证").application();
        assertThat(permissionService.list(accountA,appA.applicationId())).extracting(v->v.status()).containsExactly("NOT_APPLIED","NOT_APPLIED","NOT_APPLIED");
        assertThat(permissionService.approved(jdbc.queryForObject("select id from applications where public_id=?",Long.class,appA.applicationId()),PermissionCode.CUSTOMER_BASE_READ)).isFalse();
        permissionService.submit(accountA,appA.applicationId(),java.util.List.of(PermissionCode.CUSTOMER_BASE_READ,PermissionCode.ORDER_LIST_READ),"订单对接");
        assertThat(permissionService.list(accountA,appA.applicationId())).extracting(v->v.status()).containsExactly("PENDING_REVIEW","PENDING_REVIEW","NOT_APPLIED");
        assertThatThrownBy(()->permissionService.submit(accountA,appA.applicationId(),java.util.List.of(PermissionCode.CUSTOMER_BASE_READ),"重复")).isInstanceOf(PermissionConflictException.class);
        var firstPermission = CompletableFuture.supplyAsync(() -> permissionService.submit(accountA,appA.applicationId(),java.util.List.of(PermissionCode.ORDER_DETAIL_READ),"并发申请 A"));
        var secondPermission = CompletableFuture.supplyAsync(() -> permissionService.submit(accountA,appA.applicationId(),java.util.List.of(PermissionCode.ORDER_DETAIL_READ),"并发申请 B"));
        var permissionOutcomes = java.util.List.of(firstPermission, secondPermission).stream().map(future -> {
            try { future.join(); return "SUCCESS"; }
            catch (CompletionException failure) { assertThat(failure.getCause()).isInstanceOf(PermissionConflictException.class); return "CONFLICT"; }
        }).toList();
        assertThat(permissionOutcomes).containsExactlyInAnyOrder("SUCCESS", "CONFLICT");
        assertThatThrownBy(()->permissionService.list(accountA,appB.applicationId())).isInstanceOf(ApplicationAccessDeniedException.class);
        assertThatThrownBy(()->permissionService.submit(accountA,appB.applicationId(),java.util.List.of(PermissionCode.CUSTOMER_BASE_READ),"越权申请")).isInstanceOf(ApplicationAccessDeniedException.class);
        assertThat(permissionService.list(accountB,appB.applicationId())).extracting(v->v.status()).containsOnly("NOT_APPLIED");
        assertThatThrownBy(()->permissionService.submit(accountA,appA.applicationId(),java.util.List.of(PermissionCode.ORDER_DETAIL_READ)," ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->permissionService.submit(accountA,appA.applicationId(),java.util.Arrays.asList((PermissionCode)null),"非法空项")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->permissionService.submit(accountA,appA.applicationId(),java.util.List.of(PermissionCode.ORDER_DETAIL_READ),"\u00A0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->permissionService.submit(accountA,appA.applicationId(),java.util.List.of(PermissionCode.ORDER_DETAIL_READ),"😀".repeat(501))).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("select count(*) from permission_requests where application_id=(select id from applications where public_id=?)",Integer.class,appA.applicationId())).isEqualTo(3);
        String customerRequest=jdbc.queryForObject("select r.public_id from permission_requests r join application_permissions p on p.current_request_id=r.id where p.application_id=(select id from applications where public_id=?) and p.permission_code='CUSTOMER_BASE_READ'",String.class,appA.applicationId());
        Map<String,Object> approvedRows=jdbc.queryForMap("call review_permission_request(?,?,?,?,?,?,?)",customerRequest,"APPROVED",null,"customer-a","业务产品","复核人",java.sql.Timestamp.from(Instant.now()));
        assertThat(((Number)approvedRows.get("request_rows")).intValue()).isOne(); assertThat(((Number)approvedRows.get("permission_rows")).intValue()).isOne();
        long internalAppId=jdbc.queryForObject("select id from applications where public_id=?",Long.class,appA.applicationId());
        assertThat(permissionService.approved(internalAppId,PermissionCode.CUSTOMER_BASE_READ)).isTrue();
        assertThat(permissionService.list(accountA,appA.applicationId()).getFirst().status()).isEqualTo("APPROVED");
        assertThatThrownBy(()->permissionService.submit(accountA,appA.applicationId(),java.util.List.of(PermissionCode.CUSTOMER_BASE_READ),"已通过重复")).isInstanceOf(PermissionConflictException.class).hasMessageContaining("APPROVED");
        assertThatThrownBy(()->jdbc.update("update permission_requests set status='REJECTED',rejection_reason='非法二次审核' where public_id=?",customerRequest)).isInstanceOf(DataAccessException.class);

        String listRequest=jdbc.queryForObject("select r.public_id from permission_requests r join application_permissions p on p.current_request_id=r.id where p.application_id=? and p.permission_code='ORDER_LIST_READ'",String.class,internalAppId);
        jdbc.queryForMap("call review_permission_request(?,?,?,?,?,?,?)",listRequest,"REJECTED","请补充订单用途",null,"业务产品","复核人",java.sql.Timestamp.from(Instant.now()));
        assertThat(permissionService.approved(internalAppId,PermissionCode.ORDER_LIST_READ)).isFalse();
        assertThat(permissionService.list(accountA,appA.applicationId()).get(1).rejectionReason()).isEqualTo("请补充订单用途");
        permissionService.submit(accountA,appA.applicationId(),java.util.List.of(PermissionCode.ORDER_LIST_READ),"补充后的订单用途");
        assertThat(jdbc.queryForObject("select count(*) from permission_requests where application_id=? and permission_code='ORDER_LIST_READ'",Integer.class,internalAppId)).isEqualTo(2);

        String detailRequest=jdbc.queryForObject("select r.public_id from permission_requests r join application_permissions p on p.current_request_id=r.id where p.application_id=? and p.permission_code='ORDER_DETAIL_READ'",String.class,internalAppId);
        assertThatThrownBy(()->jdbc.queryForMap("call review_permission_request(?,?,?,?,?,?,?)",detailRequest,"APPROVED",null," ","业务产品","复核人",java.sql.Timestamp.from(Instant.now()))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.queryForMap("call review_permission_request(?,?,?,?,?,?,?)",detailRequest,"APPROVED",null,"customer-a","同一人"," 同一人 ",java.sql.Timestamp.from(Instant.now()))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.queryForMap("call review_permission_request(?,?,?,?,?,?,?)",UUID.randomUUID().toString(),"REJECTED","原因",null,"业务产品","复核人",java.sql.Timestamp.from(Instant.now()))).isInstanceOf(DataAccessException.class);
        jdbc.update("delete from application_permissions where application_id=? and permission_code='ORDER_DETAIL_READ'",internalAppId);
        assertThatThrownBy(()->jdbc.queryForMap("call review_permission_request(?,?,?,?,?,?,?)",detailRequest,"APPROVED",null,"customer-a","业务产品","复核人",java.sql.Timestamp.from(Instant.now()))).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("select status from permission_requests where public_id=?",String.class,detailRequest)).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void permissionHttpContractFailsClosedWithStableErrors() throws Exception {
        createAccount("permission_http_a", "correct horse battery staple", "APPROVED");
        createAccount("permission_http_b", "correct horse battery staple", "APPROVED");
        String accountA=jdbc.queryForObject("select public_id from accounts where normalized_username='permission_http_a'",String.class);
        String accountB=jdbc.queryForObject("select public_id from accounts where normalized_username='permission_http_b'",String.class);
        var appA=applicationService.create(accountA,"HTTP A","权限 API").application();
        var appB=applicationService.create(accountB,"HTTP B","隔离 API").application();
        Cookie session=successfulSession("permission_http_a");

        mvc.perform(get("/console/api/v1/applications/{id}/permissions",appA.applicationId()).cookie(session))
                .andExpect(status().isOk()).andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$[0].internalCustomerScope").doesNotExist()).andExpect(jsonPath("$[0].operatedBy").doesNotExist());
        mvc.perform(get("/console/api/v1/applications/not-a-uuid/permissions").cookie(session))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/console/api/v1/applications/{id}/permissions",appB.applicationId()).cookie(session))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("APPLICATION_ACCESS_DENIED"));
        mvc.perform(get("/console/api/v1/applications/{id}/permissions",appA.applicationId()))
                .andExpect(status().isUnauthorized());

        MvcResult csrfResult=mvc.perform(get("/console/api/v1/sessions/csrf").cookie(session)).andExpect(status().isOk()).andReturn();
        Map<?,?> csrfBody=mapper.readValue(csrfResult.getResponse().getContentAsByteArray(),Map.class);
        Cookie xsrf=csrfResult.getResponse().getCookie("XSRF-TOKEN");
        String invalid=mapper.writeValueAsString(Map.of("permissions",java.util.Arrays.asList((String)null),"reason","原因"));
        mvc.perform(post("/console/api/v1/applications/{id}/permissions",appA.applicationId()).cookie(session,xsrf)
                        .header("X-XSRF-TOKEN",csrfBody.get("token").toString()).contentType("application/json").content(invalid))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        String valid=mapper.writeValueAsString(Map.of("permissions",java.util.List.of("CUSTOMER_BASE_READ"),"reason","供应链客户同步"));
        mvc.perform(post("/console/api/v1/applications/{id}/permissions",appA.applicationId()).cookie(session,xsrf)
                        .header("X-XSRF-TOKEN",csrfBody.get("token").toString()).contentType("application/json").content(valid))
                .andExpect(status().isOk()).andExpect(header().exists("X-Request-ID")).andExpect(jsonPath("$[0].status").value("PENDING_REVIEW"));
        mvc.perform(post("/console/api/v1/applications/{id}/permissions",appA.applicationId()).cookie(session,xsrf)
                        .header("X-XSRF-TOKEN",csrfBody.get("token").toString()).contentType("application/json").content(valid))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PERMISSION_REQUEST_ALREADY_PENDING"));

        jdbc.execute("rename table application_permissions to application_permissions_unavailable");
        try {
            mvc.perform(get("/console/api/v1/applications/{id}/permissions",appA.applicationId()).cookie(session))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PERMISSION_SERVICE_UNAVAILABLE"))
                    .andExpect(jsonPath("$.requestId").isNotEmpty());
        } finally { jdbc.execute("rename table application_permissions_unavailable to application_permissions"); }
    }

    @Test
    void approvedCustomerCreatesOnlyOneApplicationAndSecretIsReturnedOnce(CapturedOutput output) throws Exception {
        createAccount("app_owner", "correct horse battery staple", "APPROVED");
        Cookie session = successfulSession("app_owner");
        MvcResult csrfResult = mvc.perform(get("/console/api/v1/sessions/csrf").cookie(session)).andExpect(status().isOk()).andReturn();
        Map<?, ?> csrfBody = mapper.readValue(csrfResult.getResponse().getContentAsByteArray(), Map.class);
        Cookie xsrf = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        String body = mapper.writeValueAsString(Map.of("name", "订单同步", "purpose", "查询供应链订单"));
        MvcResult created = mvc.perform(post("/console/api/v1/applications").cookie(session, xsrf)
                        .header("X-XSRF-TOKEN", csrfBody.get("token").toString()).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.appId").isNotEmpty()).andExpect(jsonPath("$.appSecret").isNotEmpty())
                .andExpect(jsonPath("$.secretShownOnce").value(true)).andReturn();
        String secret = mapper.readValue(created.getResponse().getContentAsByteArray(), Map.class).get("appSecret").toString();
        assertThat(jdbc.queryForObject("select secret_ciphertext from application_credentials", String.class)).doesNotContain(secret);
        assertThat(output.getAll()).doesNotContain(secret);
        mvc.perform(get("/console/api/v1/applications").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appId").isNotEmpty()).andExpect(jsonPath("$[0].appSecret").doesNotExist());
        mvc.perform(post("/console/api/v1/applications").cookie(session, xsrf)
                        .header("X-XSRF-TOKEN", csrfBody.get("token").toString()).contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPLICATION_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.appSecret").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from applications", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from application_credentials", Integer.class)).isOne();
    }

    @Test
    void controlledResetReplacesOneSecretAndRecordsDistinctReviewers() throws Exception {
        createAccount("reset_owner", "correct horse battery staple", "APPROVED");
        Cookie session=successfulSession("reset_owner"); MvcResult csrfResult=mvc.perform(get("/console/api/v1/sessions/csrf").cookie(session)).andReturn();
        Map<?,?> csrfBody=mapper.readValue(csrfResult.getResponse().getContentAsByteArray(),Map.class);Cookie xsrf=csrfResult.getResponse().getCookie("XSRF-TOKEN");
        MvcResult created=mvc.perform(post("/console/api/v1/applications").cookie(session,xsrf).header("X-XSRF-TOKEN",csrfBody.get("token").toString()).contentType("application/json").content("{\"name\":\"重置应用\",\"purpose\":\"沙箱联调\"}" )).andExpect(status().isCreated()).andReturn();
        Map<?,?> body=mapper.readValue(created.getResponse().getContentAsByteArray(),Map.class);String applicationId=body.get("applicationId").toString();String oldCipher=jdbc.queryForObject("select secret_ciphertext from application_credentials",String.class);
        String secret=applicationService.resetSandboxSecret(applicationId,"客户报告遗失","技术负责人","复核负责人","邮件-20260818","req_reset");
        assertThat(secret).isNotBlank();assertThat(jdbc.queryForObject("select secret_ciphertext from application_credentials",String.class)).isNotEqualTo(oldCipher).doesNotContain(secret);
        assertThat(jdbc.queryForObject("select count(*) from application_secret_reset_records where operated_by<>checked_by",Integer.class)).isOne();
        assertThatThrownBy(()->applicationService.resetSandboxSecret(applicationId,"原因","同一人","同一人","证据","req_bad")).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("select count(*) from application_secret_reset_records",Integer.class)).isOne();
    }

    @Test
    void applicationCreationIsConcurrentTenantScopedEncryptedAndTransactional() throws Exception {
        createAccount("tenant_a", "correct horse battery staple", "APPROVED");
        createAccount("tenant_b", "correct horse battery staple", "APPROVED");
        String accountA = jdbc.queryForObject("select public_id from accounts where normalized_username='tenant_a'", String.class);
        String accountB = jdbc.queryForObject("select public_id from accounts where normalized_username='tenant_b'", String.class);
        var first = CompletableFuture.supplyAsync(() -> applicationService.create(accountA, "并发应用", "订单查询"));
        var second = CompletableFuture.supplyAsync(() -> applicationService.create(accountA, "并发应用", "订单查询"));
        int successes = 0;
        int conflicts = 0;
        for (var future : java.util.List.of(first, second)) {
            try { future.join(); successes++; }
            catch (CompletionException exception) {
                assertThat(exception.getCause()).isInstanceOf(ApplicationAlreadyExistsException.class);
                conflicts++;
            }
        }
        assertThat(successes).isOne();
        assertThat(conflicts).isOne();
        var createdB = applicationService.create(accountB, "企业 B 应用", "资料查询");
        assertThat(applicationService.list(accountA)).hasSize(1).allMatch(item -> !item.appId().equals(createdB.application().appId()));
        assertThat(applicationService.list(accountB)).singleElement().extracting(ApplicationService.View::appId)
                .isEqualTo(createdB.application().appId());
        assertThat(jdbc.queryForList("select app_id from applications", String.class)).doesNotHaveDuplicates();
        assertThat(jdbc.queryForList("select secret_iv from application_credentials", String.class)).doesNotHaveDuplicates();

        Map<String, Object> credential = jdbc.queryForMap("select secret_ciphertext,secret_iv from application_credentials where application_id=(select id from applications where public_id=?)",
                createdB.application().applicationId());
        Long encryptedApplicationId = jdbc.queryForObject("select id from applications where public_id=?", Long.class,
                createdB.application().applicationId());
        byte[] key = Base64.getDecoder().decode("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=");
        Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
        decrypt.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, Base64.getDecoder().decode(credential.get("secret_iv").toString())));
        decrypt.updateAAD(("application:" + encryptedApplicationId + ":SANDBOX:app-test-v1")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(new String(decrypt.doFinal(Base64.getDecoder().decode(credential.get("secret_ciphertext").toString())), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo(createdB.appSecret());
        byte[] tampered = Base64.getDecoder().decode(credential.get("secret_ciphertext").toString());
        tampered[0] ^= 1;
        Cipher rejectTamper = Cipher.getInstance("AES/GCM/NoPadding");
        rejectTamper.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, Base64.getDecoder().decode(credential.get("secret_iv").toString())));
        rejectTamper.updateAAD(("application:" + encryptedApplicationId + ":SANDBOX:app-test-v1")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> rejectTamper.doFinal(tampered)).isInstanceOf(AEADBadTagException.class);

        createAccount("rollback_app", "correct horse battery staple", "APPROVED");
        String rollbackAccount = jdbc.queryForObject("select public_id from accounts where normalized_username='rollback_app'", String.class);
        jdbc.execute("rename table application_credentials to application_credentials_unavailable");
        try {
            assertThatThrownBy(() -> applicationService.create(rollbackAccount, "回滚应用", "回滚验证"))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            jdbc.execute("rename table application_credentials_unavailable to application_credentials");
        }
        Long rollbackEnterprise = jdbc.queryForObject("select enterprise_id from accounts where public_id=?", Long.class, rollbackAccount);
        assertThat(jdbc.queryForObject("select count(*) from applications where enterprise_id=?", Integer.class, rollbackEnterprise)).isZero();
    }

    @Test
    void applicationApiRejectsStatusDriftAndReturnsRequestIdForEmptyListAndValidationErrors() throws Exception {
        createAccount("api_owner", "correct horse battery staple", "APPROVED");
        Cookie session = successfulSession("api_owner");
        mvc.perform(get("/console/api/v1/applications").cookie(session))
                .andExpect(status().isOk()).andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$").isArray()).andExpect(jsonPath("$").isEmpty());
        MvcResult csrfResult = mvc.perform(get("/console/api/v1/sessions/csrf").cookie(session)).andReturn();
        Map<?, ?> csrf = mapper.readValue(csrfResult.getResponse().getContentAsByteArray(), Map.class);
        Cookie xsrf = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(post("/console/api/v1/applications").cookie(session, xsrf)
                        .header("X-XSRF-TOKEN", csrf.get("token").toString()).contentType("application/json")
                        .content("{\"name\":\"\",\"purpose\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        jdbc.update("update accounts set status='REJECTED' where normalized_username='api_owner'");
        mvc.perform(get("/console/api/v1/applications").cookie(session))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void controlledResetRejectsReplayBadMetadataAndRollsBackWhenAuditFails() throws Exception {
        createAccount("reset_edges", "correct horse battery staple", "APPROVED");
        String account = jdbc.queryForObject("select public_id from accounts where normalized_username='reset_edges'", String.class);
        var created = applicationService.create(account, "重置边界", "沙箱联调");
        String applicationId = created.application().applicationId();
        applicationService.resetSandboxSecret(applicationId, "客户遗失", " 技术负责人 ", "复核负责人", "邮件", "req_once");
        String onceCipher = jdbc.queryForObject("select secret_ciphertext from application_credentials", String.class);
        assertThatThrownBy(() -> applicationService.resetSandboxSecret(applicationId, "重试", "技术负责人", "复核负责人", "邮件", "req_once"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select secret_ciphertext from application_credentials", String.class)).isEqualTo(onceCipher);
        var resetA = CompletableFuture.supplyAsync(() -> applicationService.resetSandboxSecret(applicationId, "并发 A", "甲", "乙", "邮件", "req_parallel_a"));
        var resetB = CompletableFuture.supplyAsync(() -> applicationService.resetSandboxSecret(applicationId, "并发 B", "丙", "丁", "邮件", "req_parallel_b"));
        int resetSuccesses = 0;
        int staleResets = 0;
        for (var reset : java.util.List.of(resetA, resetB)) {
            try { reset.join(); resetSuccesses++; }
            catch (CompletionException exception) { assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class); staleResets++; }
        }
        assertThat(resetSuccesses).isOne();
        assertThat(staleResets).isOne();
        assertThatThrownBy(() -> applicationService.resetSandboxSecret(applicationId, "原因", " 同一人 ", "同一人", "邮件", "req_same"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> applicationService.resetSandboxSecret(applicationId, null, "甲", "乙", "邮件", "req_null"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> applicationService.resetSandboxSecret(applicationId, "x".repeat(501), "甲", "乙", "邮件", "req_long"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> applicationService.resetSandboxSecret(UUID.randomUUID().toString(), "原因", "甲", "乙", "邮件", "req_zero"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> jdbc.update("update applications set status='DISABLED' where public_id=?", applicationId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("insert into application_credentials(application_id,environment,secret_ciphertext,secret_iv,key_id,created_at,updated_at) select application_id,environment,secret_ciphertext,secret_iv,key_id,created_at,updated_at from application_credentials limit 1"))
                .isInstanceOf(DataAccessException.class);

        String beforeFailure = jdbc.queryForObject("select secret_ciphertext from application_credentials", String.class);
        jdbc.execute("rename table application_secret_reset_records to application_secret_reset_records_unavailable");
        try {
            assertThatThrownBy(() -> applicationService.resetSandboxSecret(applicationId, "审计故障", "甲", "乙", "邮件", "req_failure"))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            jdbc.execute("rename table application_secret_reset_records_unavailable to application_secret_reset_records");
        }
        assertThat(jdbc.queryForObject("select secret_ciphertext from application_credentials", String.class)).isEqualTo(beforeFailure);

        java.io.PrintStream original = System.out;
        var output = new java.io.ByteArrayOutputStream();
        try {
            System.setOut(new java.io.PrintStream(output, true, java.nio.charset.StandardCharsets.UTF_8));
            new ApplicationSecretResetRunner(applicationService, applicationId, "命令重置", "甲", "乙", "邮件", "req_runner").run(null);
        } finally {
            System.setOut(original);
        }
        assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8).lines().count()).isOne();
        assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8)).startsWith("SANDBOX_APP_SECRET=");
        output.reset();
    }

    @Test
    void authenticatesAllOnboardingStatesAndRoutesWithoutExposingSecrets() throws Exception {
        for (String state : new String[]{"PENDING_REVIEW", "REJECTED", "APPROVED"}) {
            String username = state.toLowerCase(); createAccount(username, "correct horse battery staple", state);
            Csrf csrf = csrf();
            MvcResult login = mvc.perform(post("/console/api/v1/sessions")
                            .cookie(csrf.cookies()).header("X-XSRF-TOKEN", csrf.token())
                            .contentType("application/json").content(mapper.writeValueAsString(Map.of(
                                    "login", username, "password", "correct horse battery staple"))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.onboardingStatus").value(state))
                    .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.requestId").isNotEmpty())
                    .andReturn();
            String expected = state.equals("APPROVED") ? "/dashboard" : "/onboarding/status";
            assertThat(mapper.readValue(login.getResponse().getContentAsByteArray(), Map.class).get("landingPath"))
                    .isEqualTo(expected);
        }
    }

    @Test
    void locksAccountWithGenericErrorsAndReturnsRetryAfter() throws Exception {
        createAccount("locked_user", "correct horse battery staple", "PENDING_REVIEW");
        for (int attempt = 1; attempt <= 5; attempt++) {
            Csrf csrf = csrf();
            var result = mvc.perform(post("/console/api/v1/sessions")
                    .with(request -> { request.setRemoteAddr("192.0.2.70"); return request; })
                    .cookie(csrf.cookies()).header("X-XSRF-TOKEN", csrf.token()).contentType("application/json")
                    .content(mapper.writeValueAsString(Map.of("login", "locked_user", "password", "wrong password long enough"))));
            result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        Csrf csrf = csrf();
        mvc.perform(post("/console/api/v1/sessions").cookie(csrf.cookies()).header("X-XSRF-TOKEN", csrf.token())
                        .contentType("application/json").content(mapper.writeValueAsString(Map.of(
                                "login", "locked_user", "password", "correct horse battery staple"))))
                .andExpect(status().isLocked()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("PENDING_REVIEW"))));
        Thread.sleep(1_100);
        login("locked_user", "correct horse battery staple", "192.0.2.71").andExpect(status().isOk());
    }

    @Test
    void limitsSharedIpIndependentlyFromAccountKeys() throws Exception {
        for (int attempt = 1; attempt <= 20; attempt++) {
            Csrf csrf = csrf();
            var action = mvc.perform(post("/console/api/v1/sessions")
                    .with(request -> { request.setRemoteAddr("192.0.2.90"); return request; })
                    .cookie(csrf.cookies()).header("X-XSRF-TOKEN", csrf.token()).contentType("application/json")
                    .content(mapper.writeValueAsString(Map.of("login", "unknown_" + attempt,
                            "password", "wrong password long enough"))));
            action.andExpect(status().isUnauthorized());
        }
        Csrf csrf = csrf();
        mvc.perform(post("/console/api/v1/sessions").with(request -> { request.setRemoteAddr("192.0.2.90"); return request; })
                        .cookie(csrf.cookies()).header("X-XSRF-TOKEN", csrf.token()).contentType("application/json")
                        .content(mapper.writeValueAsString(Map.of("login", "another_unknown", "password", "wrong password long enough"))))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"))
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-9][0-9]*")));
        Thread.sleep(1_100);
        login("post_expiry_unknown", "wrong password long enough", "192.0.2.90").andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnonymousProtectedAccessWithUnifiedError() throws Exception {
        mvc.perform(get("/console/api/v1/session")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void pendingAndRejectedSessionsCannotCallNonAllowlistedApis() throws Exception {
        for (String status : new String[]{"PENDING_REVIEW", "REJECTED"}) {
            String username = "restricted_" + status.toLowerCase();
            createAccount(username, "correct horse battery staple", status);
            Cookie session = successfulSession(username);
            mvc.perform(get("/console/api/v1/protected-example").cookie(session))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test
    void unknownAndKnownAccountsExposeTheSameCredentialFailure() throws Exception {
        createAccount("known_user", "correct horse battery staple", "PENDING_REVIEW");
        MvcResult known = login("known_user", "wrong password long enough", "192.0.2.120")
                .andExpect(status().isUnauthorized()).andReturn();
        MvcResult unknown = login("unknown_user", "wrong password long enough", "192.0.2.121")
                .andExpect(status().isUnauthorized()).andReturn();
        Map<?, ?> knownBody = mapper.readValue(known.getResponse().getContentAsByteArray(), Map.class);
        Map<?, ?> unknownBody = mapper.readValue(unknown.getResponse().getContentAsByteArray(), Map.class);
        assertThat(unknownBody.get("code")).isEqualTo(knownBody.get("code"));
        assertThat(unknownBody.get("message")).isEqualTo(knownBody.get("message"));
        assertThat(unknownBody.get("details")).isEqualTo(knownBody.get("details"));
        assertThat(unknownBody.get("retryable")).isEqualTo(knownBody.get("retryable"));
    }

    @Test
    void refreshesStatusOnEveryRequestAndLogoutInvalidatesOnlyCurrentSession() throws Exception {
        createAccount("session_user", "correct horse battery staple", "PENDING_REVIEW");
        Csrf initial = csrf();
        MvcResult login = mvc.perform(post("/console/api/v1/sessions").cookie(initial.cookies())
                        .header("X-XSRF-TOKEN", initial.token()).contentType("application/json")
                        .content(mapper.writeValueAsString(Map.of("login", "session_user",
                                "password", "correct horse battery staple"))))
                .andExpect(status().isOk()).andReturn();
        Cookie session = login.getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(session.getSecure()).isTrue();
        assertThat(session.getAttribute("SameSite")).isEqualToIgnoringCase("Strict");
        Csrf secondInitial = csrf();
        MvcResult secondLogin = mvc.perform(post("/console/api/v1/sessions").cookie(secondInitial.cookies())
                        .header("X-XSRF-TOKEN", secondInitial.token()).contentType("application/json")
                        .content(mapper.writeValueAsString(Map.of("login", "session_user", "password", "correct horse battery staple"))))
                .andExpect(status().isOk()).andReturn();
        Cookie secondSession = secondLogin.getResponse().getCookie("SESSION");

        jdbc.update("update accounts set status='APPROVED' where normalized_username='session_user'");
        mvc.perform(get("/console/api/v1/session").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingStatus").value("APPROVED"))
                .andExpect(jsonPath("$.landingPath").value("/dashboard"));

        MvcResult csrfResult = mvc.perform(get("/console/api/v1/sessions/csrf").cookie(session))
                .andExpect(status().isOk()).andReturn();
        Map<?, ?> csrfBody = mapper.readValue(csrfResult.getResponse().getContentAsByteArray(), Map.class);
        Cookie xsrf = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        MvcResult logout = mvc.perform(delete("/console/api/v1/session").cookie(session, xsrf)
                        .header("X-XSRF-TOKEN", csrfBody.get("token").toString()))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(logout.getResponse().getCookie("SESSION")).isNotNull();
        assertThat(logout.getResponse().getCookie("SESSION").getMaxAge()).isZero();
        mvc.perform(get("/console/api/v1/session").cookie(session)).andExpect(status().isUnauthorized());
        mvc.perform(get("/console/api/v1/session").cookie(secondSession)).andExpect(status().isOk());
    }

    @Test
    void successfulLoginClearsOnlyPriorAccountFailures() throws Exception {
        createAccount("recovered_user", "correct horse battery staple", "PENDING_REVIEW");
        for (int attempt = 0; attempt < 4; attempt++) login("recovered_user", "wrong password long enough", "192.0.2.110")
                .andExpect(status().isUnauthorized());
        login("recovered_user", "correct horse battery staple", "192.0.2.110").andExpect(status().isOk());
        for (int attempt = 0; attempt < 4; attempt++) login("recovered_user", "wrong password long enough", "192.0.2.111")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void successfulAuthenticationRotatesAnExistingSessionId() throws Exception {
        createAccount("rotation_user", "correct horse battery staple", "APPROVED");
        Cookie original = successfulSession("rotation_user");
        MvcResult csrfResult = mvc.perform(get("/console/api/v1/sessions/csrf").cookie(original))
                .andExpect(status().isOk()).andReturn();
        Map<?, ?> csrfBody = mapper.readValue(csrfResult.getResponse().getContentAsByteArray(), Map.class);
        Cookie xsrf = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        MvcResult relogin = mvc.perform(post("/console/api/v1/sessions").cookie(original, xsrf)
                        .header("X-XSRF-TOKEN", csrfBody.get("token").toString()).contentType("application/json")
                        .content(mapper.writeValueAsString(Map.of("login", "rotation_user",
                                "password", "correct horse battery staple"))))
                .andExpect(status().isOk()).andReturn();
        Cookie rotated = relogin.getResponse().getCookie("SESSION");
        assertThat(rotated).isNotNull();
        assertThat(rotated.getValue()).isNotEqualTo(original.getValue());
        mvc.perform(get("/console/api/v1/session").cookie(original)).andExpect(status().isUnauthorized());
        mvc.perform(get("/console/api/v1/session").cookie(rotated)).andExpect(status().isOk());
    }

    @Test
    void returnsOnlyCurrentAccountsReviewAndAppliesDatabaseDecisionOnNextRequest() throws Exception {
        createAccount("review_a", "correct horse battery staple", "PENDING_REVIEW");
        createAccount("review_b", "correct horse battery staple", "REJECTED");
        jdbc.update("update registration_applications r join accounts a on a.id=r.account_id "
                + "set r.rejection_reason='其他企业原因',r.reviewed_by='商务',r.reviewed_by_checker='复核',r.reviewed_at=UTC_TIMESTAMP(6) "
                + "where a.normalized_username='review_b'");
        Cookie session = successfulSession("review_a");
        mvc.perform(get("/console/api/v1/onboarding/status").cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.rejectionReason").isEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.nextAction").value("等待商务专员审核"));

        transactions.executeWithoutResult(transaction -> {
            int applicationRows = jdbc.update("""
                    update registration_applications r join accounts a on a.id=r.account_id
                    set r.status='APPROVED',r.rejection_reason=null,r.reviewed_by='商务',r.reviewed_by_checker='复核',
                        r.reviewed_at=UTC_TIMESTAMP(6),r.updated_at=UTC_TIMESTAMP(6)
                    where a.normalized_username='review_a' and r.status='PENDING_REVIEW' and a.status='PENDING_REVIEW'
                    """);
            int accountRows = jdbc.update("""
                    update accounts a join registration_applications r on r.account_id=a.id
                    set a.status='APPROVED',a.updated_at=UTC_TIMESTAMP(6)
                    where a.normalized_username='review_a' and r.status='APPROVED' and a.status='PENDING_REVIEW'
                    """);
            assertThat(applicationRows).isOne(); assertThat(accountRows).isOne();
        });
        mvc.perform(get("/console/api/v1/onboarding/status").cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.nextAction").value("进入平台创建应用"));
        mvc.perform(get("/console/api/v1/session").cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.landingPath").value("/dashboard"));
    }

    @Test
    void rejectsAnonymousOnboardingStatusQuery() throws Exception {
        mvc.perform(get("/console/api/v1/onboarding/status")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void enforcesReviewReasonAndRejectsStaleReviewTargets() {
        createAccount("review_constraints", "correct horse battery staple", "PENDING_REVIEW");
        assertThatThrownBy(() -> jdbc.update("update registration_applications set status='REJECTED' where rejection_reason is null"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update registration_applications set status='UNKNOWN'"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                update registration_applications set status='APPROVED',reviewed_by='同一人',
                reviewed_by_checker='同一人',reviewed_at=UTC_TIMESTAMP(6)
                """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                update registration_applications set status='APPROVED',rejection_reason='不得残留',
                reviewed_by='商务',reviewed_by_checker='复核',reviewed_at=UTC_TIMESTAMP(6)
                """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                update registration_applications set status='APPROVED',rejection_reason=null,
                reviewed_by=null,reviewed_by_checker=null,reviewed_at=null
                """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        int changed = jdbc.update("update registration_applications set status='APPROVED' where public_id=? and status='REJECTED'",
                UUID.randomUUID().toString());
        assertThat(changed).isZero();
    }

    @Test
    void rollsBackBothReviewTargetsWhenSecondGuardDoesNotMatch() {
        createAccount("rollback_review", "correct horse battery staple", "PENDING_REVIEW");
        transactions.executeWithoutResult(transaction -> {
            int applicationRows = jdbc.update("""
                    update registration_applications r join accounts a on a.id=r.account_id
                    set r.status='APPROVED',r.reviewed_by='商务',r.reviewed_by_checker='复核',
                        r.reviewed_at=UTC_TIMESTAMP(6),r.updated_at=UTC_TIMESTAMP(6)
                    where a.normalized_username='rollback_review' and r.status='PENDING_REVIEW'
                    """);
            int accountRows = jdbc.update("update accounts set status='APPROVED' where normalized_username='missing_account'");
            assertThat(applicationRows).isOne(); assertThat(accountRows).isZero(); transaction.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("""
                select r.status from registration_applications r join accounts a on a.id=r.account_id
                where a.normalized_username='rollback_review'
                """, String.class)).isEqualTo("PENDING_REVIEW");
        assertThat(jdbc.queryForObject("select status from accounts where normalized_username='rollback_review'", String.class))
                .isEqualTo("PENDING_REVIEW");
    }

    @Test
    void returnsServiceUnavailableForMissingApplicationAndDatabaseFailure() throws Exception {
        createAccount("unavailable_review", "correct horse battery staple", "PENDING_REVIEW");
        Cookie session = successfulSession("unavailable_review");
        jdbc.update("delete from registration_applications");
        mvc.perform(get("/console/api/v1/onboarding/status").cookie(session))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AUTH_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        createApplicationFor("unavailable_review", "PENDING_REVIEW");
        jdbc.execute("rename table registration_applications to registration_applications_unavailable");
        try {
            mvc.perform(get("/console/api/v1/onboarding/status").cookie(session))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.requestId").isNotEmpty());
        } finally {
            jdbc.execute("rename table registration_applications_unavailable to registration_applications");
        }
        jdbc.update("delete from registration_applications");
        jdbc.update("delete from accounts where normalized_username='unavailable_review'");
        mvc.perform(get("/console/api/v1/onboarding/status").cookie(session))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void rejectsExpiredSessionOnOnboardingStatusEndpoint() throws Exception {
        createAccount("expired_review", "correct horse battery staple", "PENDING_REVIEW");
        Cookie session = successfulSession("expired_review");
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        mvc.perform(get("/console/api/v1/onboarding/status").cookie(session))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private Csrf csrf() throws Exception {
        MvcResult result = mvc.perform(get("/console/api/v1/sessions/csrf")).andExpect(status().isOk()).andReturn();
        Map<?, ?> body = mapper.readValue(result.getResponse().getContentAsByteArray(), Map.class);
        return new Csrf(result.getResponse().getCookies(), body.get("token").toString());
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password, String address) throws Exception {
        Csrf csrf = csrf();
        return mvc.perform(post("/console/api/v1/sessions").with(request -> { request.setRemoteAddr(address); return request; })
                .cookie(csrf.cookies()).header("X-XSRF-TOKEN", csrf.token()).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("login", username, "password", password))));
    }

    private Cookie successfulSession(String username) throws Exception {
        MvcResult result = login(username, "correct horse battery staple", "192.0.2.200")
                .andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    private void createAccount(String username, String password, String status) {
        Instant now = Instant.now(); String enterprise = UUID.randomUUID().toString();
        jdbc.update("insert into enterprises(public_id,name,created_at,updated_at) values(?,?,?,?)",
                enterprise, username + " enterprise", now, now);
        Long enterpriseId = jdbc.queryForObject("select id from enterprises where public_id=?", Long.class, enterprise);
        jdbc.update("""
                insert into accounts(enterprise_id,public_id,username,normalized_username,contact_name,
                contact_mobile_ciphertext,contact_mobile_key_id,contact_mobile_fingerprint,password_hash,status,created_at,updated_at)
                values(?,?,?,?,?,?,?,?,?,?,?,?)
                """, enterpriseId, UUID.randomUUID().toString(), username, username, "测试联系人", "ciphertext",
                "test-v1", UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""),
                passwords.encode(password), status, now, now);
        createApplicationFor(username, status);
    }

    private void createApplicationFor(String username, String status) {
        Instant now = Instant.now();
        Long enterpriseId = jdbc.queryForObject("select enterprise_id from accounts where normalized_username=?", Long.class, username);
        Long accountId = jdbc.queryForObject("select id from accounts where normalized_username=?", Long.class, username);
        boolean terminal = !status.equals("PENDING_REVIEW");
        jdbc.update("""
                insert into registration_applications(enterprise_id,account_id,public_id,status,rejection_reason,
                reviewed_by,reviewed_by_checker,reviewed_at,submitted_at,created_at,updated_at)
                values(?,?,?,?,?,?,?,?,?,?,?)
                """, enterpriseId, accountId, UUID.randomUUID().toString(), status,
                status.equals("REJECTED") ? "测试驳回原因" : null, terminal ? "测试商务" : null,
                terminal ? "测试复核" : null, terminal ? now : null, now, now, now);
    }

    private record Csrf(Cookie[] cookies, String token) {}
}
