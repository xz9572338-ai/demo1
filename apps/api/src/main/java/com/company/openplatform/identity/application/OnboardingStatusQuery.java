package com.company.openplatform.identity.application;

import com.company.openplatform.identity.domain.RegistrationStatus;
import java.time.Instant;
import java.util.Optional;

public interface OnboardingStatusQuery {
    Optional<Result> findByAccountPublicId(String accountId);
    record Result(RegistrationStatus accountStatus, RegistrationStatus applicationStatus,
            Instant submittedAt, Instant updatedAt, String rejectionReason) {}
}
