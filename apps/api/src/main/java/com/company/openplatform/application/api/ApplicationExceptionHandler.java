package com.company.openplatform.application.api;

import com.company.openplatform.application.application.ApplicationAccessDeniedException;
import com.company.openplatform.application.application.ApplicationAlreadyExistsException;
import com.company.openplatform.application.application.ApplicationAuthenticationRequiredException;
import com.company.openplatform.shared.api.ApiError;
import com.company.openplatform.shared.api.ApiError.FieldError;
import com.company.openplatform.shared.observability.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ApplicationController.class)
public class ApplicationExceptionHandler {
    @ExceptionHandler(ApplicationAlreadyExistsException.class)
    ResponseEntity<ApiError> duplicate(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "APPLICATION_ALREADY_EXISTS", "每个企业仅可创建一个应用", false, request);
    }

    @ExceptionHandler(ApplicationAccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "ONBOARDING_NOT_APPROVED", "入驻审核通过后方可使用应用功能", false, request);
    }

    @ExceptionHandler(ApplicationAuthenticationRequiredException.class)
    ResponseEntity<ApiError> authenticationRequired(HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "请重新登录", false, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldError> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getCode().toUpperCase(),
                        "NotBlank".equals(error.getCode()) ? "该字段不能为空" : "字段长度不符合要求"))
                .toList();
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", "请检查填写内容",
                requestId(request), details, false));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformed(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "请求内容无法解析", false, request);
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message, boolean retryable,
                                              HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(code, message, requestId(request), List.of(), retryable));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }
}
