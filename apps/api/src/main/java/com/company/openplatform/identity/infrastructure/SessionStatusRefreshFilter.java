package com.company.openplatform.identity.infrastructure;

import com.company.openplatform.identity.application.AccountPrincipal;
import com.company.openplatform.identity.domain.AccountAuthenticationRepository;
import com.company.openplatform.identity.domain.AuthenticationServiceUnavailableException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class SessionStatusRefreshFilter extends OncePerRequestFilter {
    private final AccountAuthenticationRepository accounts;
    public SessionStatusRefreshFilter(AccountAuthenticationRepository accounts) { this.accounts = accounts; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && current.isAuthenticated() && current.getPrincipal() instanceof AccountPrincipal principal) {
            java.util.Optional<AccountAuthenticationRepository.AccountAuthentication> account;
            try {
                account = accounts.findByPublicId(principal.accountId());
            } catch (DataAccessException exception) {
                throw new AuthenticationServiceUnavailableException(exception);
            }
            if (account.isPresent()) {
                var refreshed = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                        List.of(new SimpleGrantedAuthority("ONBOARDING_" + account.orElseThrow().status().name())));
                SecurityContextHolder.getContext().setAuthentication(refreshed);
            } else {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String contextRelative = context == null || context.isEmpty() ? uri
                : uri.startsWith(context) ? uri.substring(context.length()) : uri;
        return "DELETE".equals(request.getMethod()) && "/console/api/v1/session".equals(contextRelative);
    }
}
