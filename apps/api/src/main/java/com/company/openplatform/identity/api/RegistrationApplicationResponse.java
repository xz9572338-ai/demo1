package com.company.openplatform.identity.api;

import java.time.Instant;
import java.util.List;

public record RegistrationApplicationResponse(
        String applicationId, String status, Instant submittedAt, String reviewRole,
        List<String> supportChannels, String nextAction, String requestId) {}
