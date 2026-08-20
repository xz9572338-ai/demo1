package com.company.openplatform.application.api;
import java.time.Instant;
public record ApplicationResponse(String applicationId,String name,String purpose,String appId,String environment,String status,
 Instant createdAt,Instant updatedAt,String requestId) {}
