package com.company.openplatform.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.company.openplatform.identity.domain.AuthenticationServiceUnavailableException;
import tools.jackson.databind.ObjectMapper;

class RequestIdFilterTest {
    @Test
    void dependencyFailureIsClosedWithStableRetryable503() throws Exception {
        var response = new MockHttpServletResponse();
        new RequestIdFilter(new ObjectMapper()).doFilter(new MockHttpServletRequest(), response,
                (request, ignored) -> { throw new DataAccessResourceFailureException("redis://secret-host"); });
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader(RequestIdFilter.HEADER)).startsWith("req_");
        assertThat(response.getContentAsString()).contains("AUTH_SERVICE_UNAVAILABLE", "\"retryable\":true")
                .doesNotContain("secret-host", "redis://");
    }

    @Test
    void sessionFailureAfterResponseWriteReplacesBufferedBodyWith503() throws Exception {
        var response = new MockHttpServletResponse();
        new RequestIdFilter(new ObjectMapper()).doFilter(new MockHttpServletRequest(), response,
                (request, downstream) -> {
                    downstream.getWriter().write("sensitive partial response");
                    throw new AuthenticationServiceUnavailableException(
                            new DataAccessResourceFailureException("redis://secret-host"));
                });
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("AUTH_SERVICE_UNAVAILABLE")
                .doesNotContain("sensitive partial response", "secret-host", "redis://");
    }
}
