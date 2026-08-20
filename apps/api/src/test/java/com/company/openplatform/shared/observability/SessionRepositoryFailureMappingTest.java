package com.company.openplatform.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.openplatform.shared.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.MapSession;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.HeaderHttpSessionIdResolver;
import org.springframework.session.web.http.SessionRepositoryFilter;
import tools.jackson.databind.ObjectMapper;

class SessionRepositoryFailureMappingTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsSessionCreationFailureToRetryable503WithoutSuccessBody() throws Exception {
        SessionRepository<MapSession> repository = repository();
        when(repository.createSession()).thenThrow(failure("create"));

        MockHttpServletResponse response = execute(repository, new MockHttpServletRequest("POST", "/login"),
                request -> request.getSession(true));

        assertUnavailable(response);
    }

    @Test
    void mapsSessionReadFailureToRetryable503WithoutProtectedAccess() throws Exception {
        SessionRepository<MapSession> repository = repository();
        when(repository.findById("existing-session")).thenThrow(failure("read"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/protected");
        request.addHeader("X-Auth-Token", "existing-session");

        MockHttpServletResponse response = execute(repository, request,
                wrapped -> wrapped.getSession(false));

        assertUnavailable(response);
    }

    @Test
    void mapsSessionDeletionFailureToRetryable503InsteadOfLogoutSuccess() throws Exception {
        SessionRepository<MapSession> repository = repository();
        MapSession session = new MapSession("existing-session");
        session.setMaxInactiveInterval(Duration.ofMinutes(30));
        when(repository.findById("existing-session")).thenReturn(session);
        org.mockito.Mockito.doThrow(failure("delete")).when(repository).deleteById("existing-session");
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/logout");
        request.addHeader("X-Auth-Token", "existing-session");

        MockHttpServletResponse response = execute(repository, request,
                wrapped -> wrapped.getSession(false).invalidate());

        assertUnavailable(response);
    }

    @SuppressWarnings("unchecked")
    private static SessionRepository<MapSession> repository() {
        return mock(SessionRepository.class);
    }

    private MockHttpServletResponse execute(SessionRepository<MapSession> repository,
            MockHttpServletRequest request, SessionAction action) throws Exception {
        SessionRepositoryFilter<MapSession> sessions = new SessionRepositoryFilter<>(repository);
        sessions.setHttpSessionIdResolver(new HeaderHttpSessionIdResolver("X-Auth-Token"));
        RequestIdFilter requestIds = new RequestIdFilter(mapper);
        MockHttpServletResponse response = new MockHttpServletResponse();
        requestIds.doFilter(request, response,
                (sessionRequest, sessionResponse) -> sessions.doFilter(sessionRequest, sessionResponse,
                        (wrappedRequest, ignored) -> {
                            try {
                                action.run((HttpServletRequest) wrappedRequest);
                            } catch (RuntimeException exception) {
                                throw exception;
                            } catch (Exception exception) {
                                throw new jakarta.servlet.ServletException(exception);
                            }
                        }));
        return response;
    }

    private void assertUnavailable(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(503);
        ApiError error = mapper.readValue(response.getContentAsByteArray(), ApiError.class);
        assertThat(error.code()).isEqualTo("AUTH_SERVICE_UNAVAILABLE");
        assertThat(error.retryable()).isTrue();
        assertThat(error.requestId()).startsWith("req_");
        assertThat(response.getContentAsString()).doesNotContain("create", "read", "delete");
    }

    private static DataAccessResourceFailureException failure(String operation) {
        return new DataAccessResourceFailureException("redis " + operation + " failed at redis.internal:6379");
    }

    @FunctionalInterface
    private interface SessionAction {
        void run(HttpServletRequest request) throws Exception;
    }
}
