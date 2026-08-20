package com.company.openplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentLinkedQueue;
import com.company.openplatform.identity.application.SubmitRegistrationApplicationUseCase;
import com.company.openplatform.identity.application.SubmitRegistrationCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletRequest;
import jakarta.servlet.http.Cookie;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.company.openplatform.shared.security.RegistrationRateLimiter;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "open-platform.security.phone-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "open-platform.security.phone-key-id=test-v1",
        "open-platform.security.app-secret-key=BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
        "open-platform.security.app-secret-key-id=app-test-v1",
        "open-platform.security.login.identifier-hmac-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "open-platform.security.registration-rate.abuse-per-minute=10",
        "open-platform.security.registration-rate.business-per-minute=2"})
@ExtendWith(OutputCaptureExtension.class)
class RegistrationApplicationIntegrationTest {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.7");
    @Container @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.4.0-alpine").withExposedPorts(6379);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired SubmitRegistrationApplicationUseCase useCase;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void cleanRegistrationData() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        jdbc.update("delete from registration_applications");
        jdbc.update("delete from accounts");
        jdbc.update("delete from enterprises");
    }

    @Test
    void submitsAtomicallyAndNeverPersistsOrLogsSensitivePlaintext(CapturedOutput output) throws Exception {
        Csrf csrf = csrf();
        String mobile = "13812345678";
        String password = "correct horse battery staple";
        String body = objectMapper.writeValueAsString(Map.of(
                "enterpriseName", "示例供应链有限公司", "contactName", "张晓英",
                "contactMobile", mobile, "username", "XiaoYing_01", "password", password));

        mvc.perform(post("/console/api/v1/registration-applications")
                        .session(csrf.session()).cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.contactMobile").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        assertThat(jdbc.queryForObject("select count(*) from enterprises", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from accounts", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from registration_applications", Integer.class)).isEqualTo(1);
        Map<String, Object> account = jdbc.queryForMap("select * from accounts limit 1");
        assertThat(account.get("password_hash").toString()).doesNotContain(password).startsWith("{");
        assertThat(passwordEncoder.matches(password, account.get("password_hash").toString())).isTrue();
        assertThat(account.get("contact_mobile_ciphertext").toString()).doesNotContain(mobile);
        assertThat(account.get("normalized_username")).isEqualTo("xiaoying_01");

        Csrf duplicateCsrf = csrf();
        mvc.perform(post("/console/api/v1/registration-applications")
                        .session(duplicateCsrf.session()).cookie(duplicateCsrf.cookie())
                        .header(duplicateCsrf.header(), duplicateCsrf.token())
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        assertThat(jdbc.queryForObject("select count(*) from enterprises", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from accounts", Integer.class)).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain(mobile).doesNotContain(password);
    }

    @Test
    void normalizesReadableMobileAndRejectsNamesInvalidAfterTrimming() throws Exception {
        Csrf csrf = csrf();
        String body = objectMapper.writeValueAsString(Map.of(
                "enterpriseName", "  示例企业  ", "contactName", "  张晓英  ",
                "contactMobile", "138-1234 5678", "username", "  Normal_User  ",
                "password", "长密码可包含中文字符且远超过七十二字节边界-1234567890-ABCDEFGHIJ"));
        mvc.perform(post("/console/api/v1/registration-applications")
                        .session(csrf.session()).cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
        Map<String, Object> account = jdbc.queryForMap("select * from accounts limit 1");
        assertThat(account.get("contact_name")).isEqualTo("张晓英");
        assertThat(account.get("normalized_username")).isEqualTo("normal_user");
        assertThat(passwordEncoder.matches("长密码可包含中文字符且远超过七十二字节边界-1234567890-ABCDEFGHIJ",
                account.get("password_hash").toString())).isTrue();

        Csrf invalidCsrf = csrf();
        String invalid = objectMapper.writeValueAsString(Map.of(
                "enterpriseName", " A ", "contactName", "张晓英", "contactMobile", "13912345678",
                "username", "another_user", "password", "correct horse battery staple"));
        mvc.perform(post("/console/api/v1/registration-applications")
                        .session(invalidCsrf.session()).cookie(invalidCsrf.cookie())
                        .header(invalidCsrf.header(), invalidCsrf.token())
                        .contentType("application/json").content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("enterpriseName"));
    }

    @Test
    void returnsContractErrorsForMalformedAndOversizedBodiesWithUniqueRequestIds() throws Exception {
        Csrf csrf = csrf();
        MvcResult malformed = mvc.perform(post("/console/api/v1/registration-applications")
                        .session(csrf.session()).cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                        .header("X-Request-ID", "client_reused_identifier")
                        .contentType("application/json").content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.requestId").isNotEmpty()).andReturn();
        Csrf otherCsrf = csrf();
        MvcResult other = mvc.perform(post("/console/api/v1/registration-applications")
                        .session(otherCsrf.session()).cookie(otherCsrf.cookie())
                        .header(otherCsrf.header(), otherCsrf.token())
                        .header("X-Request-ID", "client_reused_identifier")
                        .contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(malformed.getResponse().getHeader("X-Request-ID"))
                .isNotEqualTo(other.getResponse().getHeader("X-Request-ID"));

        mvc.perform(post("/console/api/v1/registration-applications")
                        .with(request -> { request.setRemoteAddr("192.0.2.20"); return request; })
                        .contentType("application/json").content("x".repeat(17 * 1024)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));

        Csrf emptyCsrf = csrf();
        mvc.perform(post("/console/api/v1/registration-applications")
                        .session(emptyCsrf.session()).cookie(emptyCsrf.cookie())
                        .header(emptyCsrf.header(), emptyCsrf.token()).contentType("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        Csrf typeCsrf = csrf();
        mvc.perform(post("/console/api/v1/registration-applications")
                        .session(typeCsrf.session()).cookie(typeCsrf.cookie())
                        .header(typeCsrf.header(), typeCsrf.token()).contentType("text/plain").content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

        Csrf charsetCsrf = csrf();
        mvc.perform(post("/console/api/v1/registration-applications")
                        .session(charsetCsrf.session()).cookie(charsetCsrf.cookie())
                        .header(charsetCsrf.header(), charsetCsrf.token())
                        .contentType("application/json;charset=UTF-16").content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void validatesUnicodeLengthsByCodePointRatherThanUtf16Units() throws Exception {
        Csrf csrf = csrf();
        String twelveCodePoints = "😀😀😀😀😀😀😀😀😀😀😀😀";
        String body = objectMapper.writeValueAsString(Map.of(
                "enterpriseName", "表情企业", "contactName", "张晓英", "contactMobile", "13712345678",
                "username", "unicode_user", "password", twelveCodePoints));
        mvc.perform(post("/console/api/v1/registration-applications")
                        .session(csrf.session()).cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsUnknownJsonPropertiesForbiddenByTheContract() throws Exception {
        Csrf csrf = csrf();
        String body = objectMapper.writeValueAsString(Map.of(
                "enterpriseName", "字段契约企业", "contactName", "张晓英", "contactMobile", "13612345678",
                "username", "unknown_field_user", "password", "correct horse battery staple", "unexpected", true));
        mvc.perform(post("/console/api/v1/registration-applications")
                        .session(csrf.session()).cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void anonymousAuthorizationIsLimitedToRegistrationMethods() throws Exception {
        mvc.perform(get("/console/api/v1/protected-example"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get("/console/api/v1/registration-applications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void rateLimitsAnonymousRegistrationByClientAddress() throws Exception {
        for (int attempt = 1; attempt <= 11; attempt++) {
            var request = post("/console/api/v1/registration-applications")
                    .with(value -> { value.setRemoteAddr("192.0.2.44"); return value; })
                    .contentType("application/json").content("{}");
            if (attempt <= 10) {
                mvc.perform(request).andExpect(status().isForbidden());
            } else {
                mvc.perform(request).andExpect(status().isTooManyRequests())
                        .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                                .string("Retry-After", "60"));
            }
        }
        assertThat(redis.keys("rate:registration:business:*")).isEmpty();
    }

    @Test
    void ignoresForwardedAddressFromUntrustedPeersAndSeparatesQuotaNamespaces() throws Exception {
        mvc.perform(post("/console/api/v1/registration-applications")
                        .with(request -> { request.setRemoteAddr("192.0.2.88"); return request; })
                        .header("X-Forwarded-For", "198.51.100.7").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        assertThat(redis.keys("rate:registration:abuse:192.0.2.88:*")).hasSize(1);
        assertThat(redis.keys("rate:registration:abuse:198.51.100.7:*")).isEmpty();
        assertThat(redis.keys("rate:registration:business:*")).isEmpty();
    }

    @Test
    void sharesRedisQuotaAcrossLimiterInstancesAndUsesRightmostUntrustedProxyHop() {
        var first = new RegistrationRateLimiter(redis, "127.0.0.1,::1", 1, 1);
        var second = new RegistrationRateLimiter(redis, "127.0.0.1,::1", 1, 1);
        var direct = new MockHttpServletRequest();
        direct.setRemoteAddr("192.0.2.99");
        assertThat(first.consumeAbuse(direct)).isTrue();
        assertThat(second.consumeAbuse(direct)).isFalse();

        var proxied = new MockHttpServletRequest();
        proxied.setRemoteAddr("127.0.0.1");
        proxied.addHeader("X-Forwarded-For", "203.0.113.66, 198.51.100.22");
        assertThat(first.consumeAbuse(proxied)).isTrue();
        assertThat(redis.keys("rate:registration:abuse:198.51.100.22:*")).hasSize(1);
        assertThat(redis.keys("rate:registration:abuse:203.0.113.66:*")).isEmpty();
    }

    @Test
    void exhaustsBusinessQuotaIndependentlyAfterSecurityAndValidation() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            Csrf csrf = csrf();
            String body = objectMapper.writeValueAsString(Map.of(
                    "enterpriseName", "业务额度企业" + attempt, "contactName", "张晓英",
                    "contactMobile", "1351234567" + attempt, "username", "business_user_" + attempt,
                    "password", "correct horse battery staple"));
            var request = post("/console/api/v1/registration-applications")
                    .with(value -> { value.setRemoteAddr("192.0.2.120"); return value; })
                    .session(csrf.session()).cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                    .contentType("application/json").content(body);
            if (attempt <= 2) mvc.perform(request).andExpect(status().isCreated());
            else mvc.perform(request).andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                            .string("Retry-After", "60"));
        }
        assertThat(redis.keys("rate:registration:business:192.0.2.120:*")).hasSize(1);
        assertThat(redis.keys("rate:registration:abuse:192.0.2.120:*")).hasSize(1);
    }

    @Test
    void rejectsInvalidDuplicateAndMissingCsrfWithoutPartialRows() throws Exception {
        String invalid = objectMapper.writeValueAsString(Map.of(
                "enterpriseName", "A", "contactName", "", "contactMobile", "123",
                "username", "bad space", "password", "short"));
        mvc.perform(post("/console/api/v1/registration-applications")
                .contentType("application/json").content(invalid))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        Csrf csrf = csrf();
        mvc.perform(post("/console/api/v1/registration-applications")
                        .session(csrf.session()).cookie(csrf.cookie()).header(csrf.header(), csrf.token())
                        .contentType("application/json").content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").exists());
        assertThat(jdbc.queryForObject("select count(*) from accounts", Integer.class)).isZero();
    }

    @Test
    void concurrentDuplicateSubmissionCreatesOnlyOneAggregate() throws Exception {
        var command = new SubmitRegistrationCommand(
                "并发测试企业", "测试联系人", "13912345678", "concurrent_user", "correct horse battery staple");
        var start = new CountDownLatch(1);
        var successes = new AtomicInteger();
        var failures = new AtomicInteger();
        var failureTypes = new ConcurrentLinkedQueue<Class<?>>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int index = 0; index < 2; index++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        useCase.submit(command);
                        successes.incrementAndGet();
                    } catch (Exception exception) {
                        failures.incrementAndGet();
                        failureTypes.add(exception.getClass());
                    }
                });
            }
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(1);
        assertThat(failureTypes).containsExactly(com.company.openplatform.identity.domain.AccountAlreadyExistsException.class);
        assertThat(jdbc.queryForObject("select count(*) from enterprises", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from accounts", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from registration_applications", Integer.class)).isEqualTo(1);
    }

    private Csrf csrf() throws Exception {
        MvcResult result = mvc.perform(get("/console/api/v1/registration-applications/csrf"))
                .andExpect(status().isOk()).andReturn();
        Map<?, ?> payload = objectMapper.readValue(result.getResponse().getContentAsByteArray(), Map.class);
        return new Csrf((MockHttpSession) result.getRequest().getSession(), result.getResponse().getCookie("XSRF-TOKEN"),
                payload.get("headerName").toString(), payload.get("token").toString());
    }

    private record Csrf(MockHttpSession session, Cookie cookie, String header, String token) {}
}
