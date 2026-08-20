package com.company.openplatform.identity.application;

import com.company.openplatform.identity.domain.AccountAuthenticationRepository;
import com.company.openplatform.identity.domain.InvalidCredentialsException;
import com.company.openplatform.identity.domain.RegistrationStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;

@Service
public final class AuthenticateAccountUseCase {
    private final AccountAuthenticationRepository accounts;
    private final PasswordEncoder passwords;
    private final LoginAttemptLimiter limiter;
    private final String dummyHash;

    public AuthenticateAccountUseCase(AccountAuthenticationRepository accounts, PasswordEncoder passwords,
            LoginAttemptLimiter limiter) {
        this.accounts = accounts; this.passwords = passwords; this.limiter = limiter;
        this.dummyHash = passwords.encode("dummy password used only to equalize authentication work");
    }

    public AuthenticationResult authenticate(String login, String password, HttpServletRequest request) {
        String normalized = login.trim().toLowerCase(Locale.ROOT);
        var keys = limiter.keys(normalized, request);
        limiter.assertAllowed(keys);
        java.util.Optional<AccountAuthenticationRepository.AccountAuthentication> account;
        boolean valid;
        try {
            account = accounts.findByLogin(normalized);
            valid = passwords.matches(password, account.map(AccountAuthenticationRepository.AccountAuthentication::passwordHash)
                    .orElse(dummyHash));
        } catch (DataAccessException | IllegalArgumentException exception) {
            limiter.abort(keys);
            throw new com.company.openplatform.identity.domain.AuthenticationServiceUnavailableException(exception);
        }
        if (account.isEmpty() || !valid) {
            limiter.failed(keys);
            throw new InvalidCredentialsException();
        }
        limiter.succeeded(keys);
        var found = account.orElseThrow();
        return new AuthenticationResult(found.publicId(), found.status(), landing(found.status()));
    }

    public AuthenticationResult refresh(String accountId) {
        AccountAuthenticationRepository.AccountAuthentication account;
        try { account = accounts.findByPublicId(accountId).orElseThrow(InvalidCredentialsException::new); }
        catch (DataAccessException | IllegalArgumentException exception) {
            throw new com.company.openplatform.identity.domain.AuthenticationServiceUnavailableException(exception);
        }
        return new AuthenticationResult(account.publicId(), account.status(), landing(account.status()));
    }

    private static String landing(RegistrationStatus status) {
        return status == RegistrationStatus.APPROVED ? "/dashboard" : "/onboarding/status";
    }

    public record AuthenticationResult(String accountId, RegistrationStatus status, String landingPath) {}
}
