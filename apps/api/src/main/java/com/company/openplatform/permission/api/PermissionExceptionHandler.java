package com.company.openplatform.permission.api;
import com.company.openplatform.application.application.ApplicationAccessDeniedException; import com.company.openplatform.permission.application.PermissionConflictException; import com.company.openplatform.shared.api.ApiError; import com.company.openplatform.shared.observability.RequestIdFilter; import jakarta.servlet.http.HttpServletRequest; import java.util.List; import org.springframework.dao.DataAccessException; import org.springframework.http.*; import org.springframework.web.bind.MethodArgumentNotValidException; import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=PermissionController.class) public class PermissionExceptionHandler {
 @ExceptionHandler({IllegalArgumentException.class,MethodArgumentNotValidException.class}) ResponseEntity<ApiError> validation(Exception e,HttpServletRequest r){return response(400,"VALIDATION_FAILED","权限申请字段无效",false,r);}
 @ExceptionHandler(ApplicationAccessDeniedException.class) ResponseEntity<ApiError> denied(Exception e,HttpServletRequest r){return response(403,"APPLICATION_ACCESS_DENIED","无权访问该应用",false,r);}
 @ExceptionHandler(PermissionConflictException.class) ResponseEntity<ApiError> conflict(PermissionConflictException e,HttpServletRequest r){return response(409,e.code(),"当前权限状态不允许重复提交",false,r);}
 @ExceptionHandler(DataAccessException.class) ResponseEntity<ApiError> unavailable(Exception e,HttpServletRequest r){return response(503,"PERMISSION_SERVICE_UNAVAILABLE","权限服务暂时不可用，请稍后重试",true,r);}
 private ResponseEntity<ApiError> response(int status,String code,String message,boolean retry,HttpServletRequest r){return ResponseEntity.status(status).body(new ApiError(code,message,(String)r.getAttribute(RequestIdFilter.ATTRIBUTE),List.of(),retry));}
}
