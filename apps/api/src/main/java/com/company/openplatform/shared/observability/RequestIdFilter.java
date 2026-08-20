package com.company.openplatform.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.ObjectMapper;
import com.company.openplatform.shared.api.ApiError;
import com.company.openplatform.identity.domain.AuthenticationServiceUnavailableException;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    public static final String HEADER = "X-Request-ID";
    private final ObjectMapper objectMapper;

    public RequestIdFilter(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = "req_" + UUID.randomUUID();
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        var bufferedResponse = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, bufferedResponse);
            bufferedResponse.copyBodyToResponse();
        } catch (DataAccessException | AuthenticationServiceUnavailableException exception) {
            if (response.isCommitted()) throw exception;
            bufferedResponse.resetBuffer();
            bufferedResponse.setStatus(503);
            bufferedResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            bufferedResponse.setHeader(HEADER, requestId);
            boolean openApi = request.getRequestURI().startsWith("/sandbox/v1/") || request.getRequestURI().startsWith("/openapi/v1/");
            objectMapper.writeValue(bufferedResponse.getOutputStream(), new ApiError(openApi ? "SERVICE_UNAVAILABLE" : "AUTH_SERVICE_UNAVAILABLE",
                    openApi ? "服务暂时不可用，请稍后重试" : "认证服务暂时不可用，请稍后重试", requestId, List.of(), true));
            bufferedResponse.copyBodyToResponse();
        }
    }
}
