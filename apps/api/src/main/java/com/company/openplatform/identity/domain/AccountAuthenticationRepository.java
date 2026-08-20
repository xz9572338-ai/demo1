package com.company.openplatform.identity.domain;

import java.util.Optional;

public interface AccountAuthenticationRepository {
    Optional<AccountAuthentication> findByLogin(String login);
    Optional<AccountAuthentication> findByPublicId(String publicId);

    record AccountAuthentication(String publicId, String passwordHash, RegistrationStatus status) {}
}
