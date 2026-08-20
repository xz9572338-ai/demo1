package com.company.openplatform.application.api;
import java.time.Instant;
public record ApplicationCreatedResponse(String applicationId,String name,String purpose,String appId,String appSecret,
 String environment,String status,boolean secretShownOnce,Instant createdAt,Instant updatedAt,String requestId) {}
