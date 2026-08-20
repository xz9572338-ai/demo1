package com.company.openplatform.identity.api;

public record SessionContextResponse(String accountId, String onboardingStatus, String landingPath, String requestId) {}
