package com.company.openplatform.identity.api;

import com.company.openplatform.identity.domain.AuthenticationServiceUnavailableException;
import com.company.openplatform.identity.domain.InvalidCredentialsException;
import com.company.openplatform.identity.domain.LoginLockedException;
import com.company.openplatform.shared.api.ApiError;
import com.company.openplatform.shared.observability.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {ConsoleSessionController.class, OnboardingStatusController.class})
public final class AuthenticationExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error("VALIDATION_FAILED", "请检查登录信息", request, false));
    }
    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiError> invalidCredentials(InvalidCredentialsException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(error("INVALID_CREDENTIALS", "账号或密码不正确", request, false));
    }
    @ExceptionHandler(LoginLockedException.class)
    ResponseEntity<ApiError> locked(LoginLockedException exception, HttpServletRequest request) {
        HttpStatus status = exception.ipLimited() ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.LOCKED;
        String code = exception.ipLimited() ? "LOGIN_RATE_LIMITED" : "LOGIN_TEMPORARILY_LOCKED";
        return ResponseEntity.status(status).header("Retry-After", Long.toString(exception.retryAfter()))
                .body(error(code, "登录暂时受限，请稍后重试或通过企业微信/邮件联系技术对接负责人", request, true));
    }
    @ExceptionHandler(AuthenticationServiceUnavailableException.class)
    ResponseEntity<ApiError> unavailable(AuthenticationServiceUnavailableException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(error("AUTH_SERVICE_UNAVAILABLE", "认证服务暂时不可用，请稍后重试", request, true));
    }
    private static ApiError error(String code, String message, HttpServletRequest request, boolean retryable) {
        return new ApiError(code, message, (String) request.getAttribute(RequestIdFilter.ATTRIBUTE), List.of(), retryable);
    }
}
