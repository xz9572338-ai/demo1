package com.company.openplatform.identity.api;

import java.time.Instant;
import java.util.List;

public record OnboardingStatusResponse(String status, Instant submittedAt, Instant updatedAt,
        String rejectionReason, String reviewRole, List<String> supportChannels,
        String nextAction, String requestId) {}
