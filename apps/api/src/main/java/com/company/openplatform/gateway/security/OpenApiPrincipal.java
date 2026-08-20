package com.company.openplatform.gateway.security;

import com.company.openplatform.permission.domain.PermissionCode;

public record OpenApiPrincipal(long enterpriseId, long applicationId, String environment,
                               PermissionCode permissionCode, String internalCustomerScope,
                               String requestId, String traceId) {}
