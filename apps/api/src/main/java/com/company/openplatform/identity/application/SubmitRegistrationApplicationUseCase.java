package com.company.openplatform.identity.application;

import com.company.openplatform.identity.domain.MobileProtector;
import com.company.openplatform.identity.domain.RegistrationRepository;
import com.company.openplatform.identity.domain.RegistrationRepository.RegistrationDraft;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmitRegistrationApplicationUseCase {
    private final RegistrationRepository repository;
    private final MobileProtector mobileProtector;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public SubmitRegistrationApplicationUseCase(
            RegistrationRepository repository, MobileProtector mobileProtector,
            PasswordEncoder passwordEncoder, Clock clock) {
        this.repository = repository;
        this.mobileProtector = mobileProtector;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public SubmitRegistrationResult submit(SubmitRegistrationCommand command) {
        String username = command.username().trim();
        String normalizedUsername = username.toLowerCase(Locale.ROOT);
        String mobile = command.contactMobile().replaceAll("[\\s-]", "");
        Instant now = clock.instant();
        var saved = repository.save(new RegistrationDraft(
                command.enterpriseName().trim(), command.contactName().trim(), mobileProtector.protect(mobile),
                username, normalizedUsername, passwordEncoder.encode(command.password()), now));
        return new SubmitRegistrationResult(saved.applicationId(), saved.status().name(), saved.submittedAt());
    }
}
