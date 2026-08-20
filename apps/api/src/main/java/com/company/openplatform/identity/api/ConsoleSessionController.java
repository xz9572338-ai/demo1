package com.company.openplatform.identity.api;

import com.company.openplatform.identity.application.AccountPrincipal;
import com.company.openplatform.identity.application.AuthenticateAccountUseCase;
import com.company.openplatform.identity.domain.InvalidCredentialsException;
import com.company.openplatform.shared.observability.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/console/api/v1")
public final class ConsoleSessionController {
    private final AuthenticateAccountUseCase authenticate;
    public ConsoleSessionController(AuthenticateAccountUseCase authenticate) { this.authenticate = authenticate; }

    @GetMapping("/sessions/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/sessions")
    SessionContextResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        var result = authenticate.authenticate(body.login(), body.password(), request);
        var principal = new AccountPrincipal(result.accountId());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                java.util.List.of(new SimpleGrantedAuthority("ONBOARDING_" + result.status().name())));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        request.changeSessionId();
        return response(result, request);
    }

    @GetMapping("/session")
    SessionContextResponse current(Authentication authentication, HttpServletRequest request) {
        if (!(authentication.getPrincipal() instanceof AccountPrincipal principal)) throw new InvalidCredentialsException();
        var result = authenticate.refresh(principal.accountId());
        return response(result, request);
    }

    @DeleteMapping("/session")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    private static SessionContextResponse response(AuthenticateAccountUseCase.AuthenticationResult result,
            HttpServletRequest request) {
        return new SessionContextResponse(result.accountId(), result.status().name(), result.landingPath(),
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE));
    }
}
