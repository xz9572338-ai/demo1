package com.company.openplatform.identity.api;

import com.company.openplatform.identity.domain.AccountAlreadyExistsException;
import com.company.openplatform.shared.api.ApiError;
import com.company.openplatform.shared.api.ApiError.FieldError;
import com.company.openplatform.shared.observability.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import com.company.openplatform.shared.security.RegistrationRateLimitException;
import com.company.openplatform.shared.security.RegistrationRateLimitUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RegistrationApplicationController.class)
public class RegistrationExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(RegistrationExceptionHandler.class);
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldError> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getCode().toUpperCase(),
                        safeMessage(error.getField(), error.getCode())))
                .toList();
        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_FAILED", "请检查填写内容", requestId(request), details, false));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformedBody(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiError(
                "MALFORMED_REQUEST", "请求内容无法解析", requestId(request), List.of(), false));
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    ResponseEntity<ApiError> conflict(AccountAlreadyExistsException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                "ACCOUNT_ALREADY_EXISTS", "账号已存在，请更换账号或联系技术对接负责人",
                requestId(request), List.of(new FieldError("username", "NOT_UNIQUE", "账号已存在")), false));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMedia(HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(new ApiError(
                "UNSUPPORTED_MEDIA_TYPE", "仅支持 UTF-8 JSON 请求", requestId(request), List.of(), false));
    }

    @ExceptionHandler(RegistrationRateLimitException.class)
    ResponseEntity<ApiError> rateLimited(RegistrationRateLimitException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "60").body(new ApiError(
                "RATE_LIMITED", "提交过于频繁，请稍后重试", requestId(request), List.of(), true));
    }

    @ExceptionHandler(RegistrationRateLimitUnavailableException.class)
    ResponseEntity<ApiError> rateLimitUnavailable(RegistrationRateLimitUnavailableException exception,
            HttpServletRequest request) {
        log.error("registration_failed requestId={} category=rate_limit_unavailable", requestId(request));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(
                "RATE_LIMIT_UNAVAILABLE", "提交保护暂时不可用，请稍后重试",
                requestId(request), List.of(), true));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> protectionFailure(IllegalStateException exception, HttpServletRequest request) {
        log.error("registration_failed requestId={} category=protection errorType={}",
                requestId(request), exception.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(new ApiError(
                "REGISTRATION_FAILED", "申请暂时无法提交，请稍后重试",
                requestId(request), List.of(), true));
    }


    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        log.error("registration_failed requestId={} category=unexpected errorType={}",
                requestId(request), exception.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(new ApiError(
                "REGISTRATION_FAILED", "申请暂时无法提交，请稍后重试",
                requestId(request), List.of(), true));
    }

    private static String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }

    private static String safeMessage(String field, String code) {
        return switch (code) {
            case "NotBlank" -> "该字段不能为空";
            case "Size", "CodePointSize" -> "字段长度不符合要求";
            case "Pattern" -> field.equals("contactMobile") ? "请输入有效的中国大陆手机号" : "字段格式不符合要求";
            default -> "字段值无效";
        };
    }
}
