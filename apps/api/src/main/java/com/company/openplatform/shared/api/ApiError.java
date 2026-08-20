package com.company.openplatform.shared.api;

import java.util.List;

public record ApiError(String code, String message, String requestId, List<FieldError> details, boolean retryable) {
    public record FieldError(String field, String code, String message) {}
}
