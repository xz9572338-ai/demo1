package com.company.openplatform.shared.security;

import com.company.openplatform.shared.api.ApiError;
import com.company.openplatform.shared.observability.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class AnonymousRegistrationProtectionFilter extends OncePerRequestFilter {
    static final int MAX_BODY_BYTES = 16 * 1024;
    private static final String PATH = "/console/api/v1/registration-applications";
    private final ObjectMapper objectMapper;
    private final RegistrationRateLimiter limiter;

    public AnonymousRegistrationProtectionFilter(ObjectMapper objectMapper, RegistrationRateLimiter limiter) {
        this.objectMapper = objectMapper;
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String contextRelative = context == null || context.isEmpty() ? uri
                : uri.startsWith(context) ? uri.substring(context.length()) : uri;
        return !"POST".equals(request.getMethod()) || !PATH.equals(contextRelative);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            if (!limiter.consumeAbuse(request)) {
                response.setHeader("Retry-After", "60");
                write(response, request, 429, "RATE_LIMITED", "提交过于频繁，请稍后重试", true);
                return;
            }
        } catch (RegistrationRateLimitUnavailableException exception) {
            write(response, request, 503, "RATE_LIMIT_UNAVAILABLE", "提交保护暂时不可用，请稍后重试", true);
            return;
        }

        MediaType contentType;
        try {
            contentType = request.getContentType() == null ? null : MediaType.parseMediaType(request.getContentType());
        } catch (IllegalArgumentException exception) {
            write(response, request, 415, "UNSUPPORTED_MEDIA_TYPE", "仅支持 UTF-8 JSON 请求", false);
            return;
        }
        if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                || (contentType.getCharset() != null && !StandardCharsets.UTF_8.equals(contentType.getCharset()))) {
            write(response, request, 415, "UNSUPPORTED_MEDIA_TYPE", "仅支持 UTF-8 JSON 请求", false);
            return;
        }

        long declaredLength = request.getContentLengthLong();
        if (declaredLength > MAX_BODY_BYTES) {
            write(response, request, 413, "PAYLOAD_TOO_LARGE", "请求内容超过允许大小", false);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            write(response, request, 413, "PAYLOAD_TOO_LARGE", "请求内容超过允许大小", false);
            return;
        }
        chain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private void write(HttpServletResponse response, HttpServletRequest request, int status,
                       String code, String message, boolean retryable) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiError(code, message,
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE), List.of(), retryable));
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        CachedBodyRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body.clone(); }

        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    if (listener == null) throw new IllegalArgumentException("ReadListener is required");
                    try { listener.onDataAvailable(); if (isFinished()) listener.onAllDataRead(); }
                    catch (IOException exception) { listener.onError(exception); }
                }
            };
        }

        @Override public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
