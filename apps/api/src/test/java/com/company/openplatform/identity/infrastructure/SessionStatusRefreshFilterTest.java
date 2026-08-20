package com.company.openplatform.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.openplatform.identity.application.AccountPrincipal;
import com.company.openplatform.identity.domain.AccountAuthenticationRepository;
import com.company.openplatform.identity.domain.AuthenticationServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SessionStatusRefreshFilterTest {
    @Test
    void mapsIdentityStoreFailureToAuthenticationInfrastructureFailure() {
        var accounts = mock(AccountAuthenticationRepository.class);
        when(accounts.findByPublicId("account-id"))
                .thenThrow(new DataAccessResourceFailureException("identity unavailable"));
        var request = new MockHttpServletRequest("GET", "/console/api/v1/session");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(new AccountPrincipal("account-id"), null, java.util.List.of()));
        try {
            assertThatThrownBy(() -> new SessionStatusRefreshFilter(accounts).doFilter(
                    request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> {}))
                    .isInstanceOf(AuthenticationServiceUnavailableException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void skipsLogoutRefreshBelowServletContextPath() throws Exception {
        var request = new MockHttpServletRequest("DELETE", "/gateway/console/api/v1/session");
        request.setContextPath("/gateway");
        assertThat(new SessionStatusRefreshFilter(mock(AccountAuthenticationRepository.class)).shouldNotFilter(request))
                .isTrue();
    }
}
