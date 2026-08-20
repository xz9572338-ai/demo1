package com.company.openplatform.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class AnonymousRegistrationProtectionFilterTest {
    @Test
    void rejectsOversizedBodyWhenContentLengthIsUnknown() throws Exception {
        RegistrationRateLimiter limiter = mock(RegistrationRateLimiter.class);
        when(limiter.consumeAbuse(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var filter = new AnonymousRegistrationProtectionFilter(new ObjectMapper(), limiter);
        var request = new MockHttpServletRequest("POST", "/console/api/v1/registration-applications") {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        request.setContentType("application/json");
        request.setContent("x".repeat(17 * 1024).getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void failsClosedWithContractResponseWhenRedisIsUnavailable() throws Exception {
        RegistrationRateLimiter limiter = mock(RegistrationRateLimiter.class);
        when(limiter.consumeAbuse(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RegistrationRateLimitUnavailableException(new IllegalStateException("offline")));
        var filter = new AnonymousRegistrationProtectionFilter(new ObjectMapper(), limiter);
        var request = new MockHttpServletRequest("POST", "/console/api/v1/registration-applications");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("RATE_LIMIT_UNAVAILABLE").contains("retryable");
    }

    @Test
    void protectsTheContextRelativeRegistrationPath() throws Exception {
        RegistrationRateLimiter limiter = mock(RegistrationRateLimiter.class);
        when(limiter.consumeAbuse(any())).thenReturn(true);
        var filter = new AnonymousRegistrationProtectionFilter(new ObjectMapper(), limiter);
        var request = new MockHttpServletRequest("POST", "/platform/console/api/v1/registration-applications");
        request.setContextPath("/platform");
        request.setContentType("application/json");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(limiter).consumeAbuse(any());
        verify(chain).doFilter(any(), org.mockito.ArgumentMatchers.eq(response));
    }
}
