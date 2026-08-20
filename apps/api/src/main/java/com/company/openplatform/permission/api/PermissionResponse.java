package com.company.openplatform.permission.api;
import com.company.openplatform.permission.domain.PermissionCode; import java.time.Instant;
public record PermissionResponse(PermissionCode code,String name,String purpose,String dataScope,String sensitiveNotice,String status,Instant submittedAt,Instant updatedAt,String rejectionReason,String requestId){}
