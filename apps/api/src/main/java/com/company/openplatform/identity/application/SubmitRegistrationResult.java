package com.company.openplatform.identity.application;

import java.time.Instant;

public record SubmitRegistrationResult(String applicationId, String status, Instant submittedAt) {}
