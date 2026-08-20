package com.company.openplatform.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import com.company.openplatform.identity.application.LoginAttemptLimiter;
import com.company.openplatform.identity.domain.AuthenticationServiceUnavailableException;
import com.company.openplatform.identity.domain.LoginLockedException;
import com.company.openplatform.shared.security.RegistrationRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

class LoginAttemptLimiterTest {
    private static final String HMAC_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    @Test
    void failsClosedWhenRedisCannotEvaluateLocks() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("unavailable"));
        var limiter = new LoginAttemptLimiter(redis, mock(RegistrationRateLimiter.class), HMAC_KEY, 5, 20, 900, 1800);
        var keys = new LoginAttemptLimiter.AttemptKeys("account", "192.0.2.1", "token");
        assertThatThrownBy(() -> limiter.assertAllowed(keys))
                .isInstanceOf(AuthenticationServiceUnavailableException.class);
        assertThatThrownBy(() -> limiter.failed(keys))
                .isInstanceOf(AuthenticationServiceUnavailableException.class);
        assertThatThrownBy(() -> limiter.succeeded(keys))
                .isInstanceOf(AuthenticationServiceUnavailableException.class);
        assertThatThrownBy(() -> limiter.abort(keys))
                .isInstanceOf(AuthenticationServiceUnavailableException.class);
    }

    @Test
    void rejectsInvalidProtectionConfigurationAtStartup() {
        assertThatThrownBy(() -> new LoginAttemptLimiter(mock(StringRedisTemplate.class),
                mock(RegistrationRateLimiter.class), HMAC_KEY, 0, 20, 900, 1800)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginAttemptLimiter(mock(StringRedisTemplate.class),
                mock(RegistrationRateLimiter.class), HMAC_KEY, 5, 20, Integer.MAX_VALUE, 1800)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginAttemptLimiter(mock(StringRedisTemplate.class),
                mock(RegistrationRateLimiter.class), "dG9vLXNob3J0", 5, 20, 900, 1800)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usesKeyedStableAccountIdentifiers() {
        RegistrationRateLimiter resolver = mock(RegistrationRateLimiter.class);
        when(resolver.clientAddress(any())).thenReturn("192.0.2.1");
        var first = new LoginAttemptLimiter(mock(StringRedisTemplate.class), resolver, HMAC_KEY, 5, 20, 900, 1800);
        var second = new LoginAttemptLimiter(mock(StringRedisTemplate.class), resolver,
                "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=", 5, 20, 900, 1800);
        var request = mock(jakarta.servlet.http.HttpServletRequest.class);
        assertThat(first.keys("known-user", request).account()).hasSize(64)
                .isNotEqualTo(second.keys("known-user", request).account());
    }

    @Test
    void reportsLeaseContentionAsAccountScopedWithActualTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), any(Object[].class))).thenReturn("BUSY:17");
        var limiter = new LoginAttemptLimiter(redis, mock(RegistrationRateLimiter.class), HMAC_KEY, 5, 20, 900, 1800);
        assertThatThrownBy(() -> limiter.assertAllowed(new LoginAttemptLimiter.AttemptKeys("account", "192.0.2.1", "token")))
                .isInstanceOfSatisfying(LoginLockedException.class, exception -> {
                    assertThat(exception.ipLimited()).isFalse();
                    assertThat(exception.retryAfter()).isEqualTo(17);
                });
    }
}
