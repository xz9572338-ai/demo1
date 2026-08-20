package com.company.openplatform.identity.domain;

import java.time.Instant;

public interface RegistrationRepository {
    RegistrationSaved save(RegistrationDraft draft);

    record RegistrationDraft(
            String enterpriseName, String contactName, EncryptedMobile mobile,
            String username, String normalizedUsername, String passwordHash, Instant submittedAt) {}

    record RegistrationSaved(String applicationId, RegistrationStatus status, Instant submittedAt) {}
}
