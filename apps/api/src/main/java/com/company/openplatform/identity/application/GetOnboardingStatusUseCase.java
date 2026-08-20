package com.company.openplatform.identity.application;

import com.company.openplatform.identity.domain.AuthenticationServiceUnavailableException;
import org.springframework.stereotype.Service;

@Service
public final class GetOnboardingStatusUseCase {
    private final OnboardingStatusQuery query;
    public GetOnboardingStatusUseCase(OnboardingStatusQuery query) { this.query = query; }
    public OnboardingStatusQuery.Result execute(String accountId) {
        var result = query.findByAccountPublicId(accountId)
                .orElseThrow(() -> new AuthenticationServiceUnavailableException(
                        new IllegalStateException("onboarding application missing")));
        if (result.accountStatus() != result.applicationStatus()) {
            throw new AuthenticationServiceUnavailableException(new IllegalStateException("onboarding status mismatch"));
        }
        return result;
    }
}
