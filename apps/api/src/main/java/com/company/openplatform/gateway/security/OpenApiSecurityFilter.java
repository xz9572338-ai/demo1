package com.company.openplatform.gateway.security;

import com.company.openplatform.permission.domain.PermissionCode;
import com.company.openplatform.shared.api.ApiError;
import com.company.openplatform.shared.observability.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OpenApiSecurityFilter extends OncePerRequestFilter {
    private static final Logger LOG=LoggerFactory.getLogger(OpenApiSecurityFilter.class);
    private static final Pattern APP=Pattern.compile("app_[A-Za-z0-9_-]{32}"), TIME=Pattern.compile("[0-9]{1,12}"),
            NONCE=Pattern.compile("[A-Za-z0-9_-]{16,64}"), SIGNATURE=Pattern.compile("[0-9a-f]{64}");
    private final OpenApiAuthenticationService authentication; private final ObjectMapper mapper;
    public OpenApiSecurityFilter(OpenApiAuthenticationService authentication,ObjectMapper mapper){this.authentication=authentication;this.mapper=mapper;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){String path=request.getRequestURI();return !(path.equals("/sandbox/v1")||path.startsWith("/sandbox/v1/"));}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
        String endpoint="unmapped";
        try{
            Endpoint resolved=validate(request);endpoint=resolved.key();String app=header(request,"X-App-ID",APP),timestamp=header(request,"X-Timestamp",TIME),nonce=header(request,"X-Nonce",NONCE),signature=header(request,"X-Signature",SIGNATURE);
            String requestId=(String)request.getAttribute(RequestIdFilter.ATTRIBUTE);
            OpenApiPrincipal principal=authentication.authenticate(app,timestamp,nonce,signature,OpenApiCanonicalRequest.build(request,app,timestamp,nonce),resolved.permission(),resolved.key(),requestId);
            var token=new UsernamePasswordAuthenticationToken(principal,null,List.of(new SimpleGrantedAuthority("OPENAPI_AUTHENTICATED")));
            SecurityContextHolder.getContext().setAuthentication(token);
            LOG.info("open_api_security result=AUTHORIZED requestId={} traceId={} environment={} enterpriseId={} applicationId={} endpoint={}",requestId,principal.traceId(),principal.environment(),principal.enterpriseId(),principal.applicationId(),endpoint);
            chain.doFilter(request,response);
        }catch(OpenApiFailure failure){String id=(String)request.getAttribute(RequestIdFilter.ATTRIBUTE);LOG.info("open_api_security result={} requestId={} traceId={} environment=SANDBOX endpoint={}",failure.code(),id,id,endpoint);write(response,request,failure);}
    }
    private Endpoint validate(HttpServletRequest request){
        if(!"GET".equals(request.getMethod())||request.getContentLengthLong()>0||request.getHeader("Transfer-Encoding")!=null)throw new OpenApiFailure(400,"VALIDATION_FAILED",false);
        try{if(request.getInputStream().read()!=-1)throw new OpenApiFailure(400,"VALIDATION_FAILED",false);}catch(IOException failure){throw new OpenApiFailure(400,"VALIDATION_FAILED",false);}
        String raw=request.getQueryString();if(raw!=null&&raw.getBytes(StandardCharsets.UTF_8).length>4096)throw new OpenApiFailure(400,"VALIDATION_FAILED",false);
        String path=request.getRequestURI();if(path.matches("/sandbox/v1/customers/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))return new Endpoint("customer",PermissionCode.CUSTOMER_BASE_READ);
        if(path.equals("/sandbox/v1/orders"))return new Endpoint("orders",PermissionCode.ORDER_LIST_READ);
        if(path.matches("/sandbox/v1/orders/[A-Za-z0-9_-]{1,64}"))return new Endpoint("order-detail",PermissionCode.ORDER_DETAIL_READ);
        throw new OpenApiFailure(400,"VALIDATION_FAILED",false);
    }
    private String header(HttpServletRequest request,String name,Pattern pattern){List<String> values=Collections.list(request.getHeaders(name));if(values.size()!=1||!values.getFirst().chars().allMatch(c->c<128)||!pattern.matcher(values.getFirst()).matches())throw new OpenApiFailure(400,"VALIDATION_FAILED",false);return values.getFirst();}
    private void write(HttpServletResponse response,HttpServletRequest request,OpenApiFailure failure)throws IOException{
        response.resetBuffer();response.setStatus(failure.status());response.setContentType(MediaType.APPLICATION_JSON_VALUE);String id=(String)request.getAttribute(RequestIdFilter.ATTRIBUTE);response.setHeader(RequestIdFilter.HEADER,id);
        if(failure.retryAfter()!=null)response.setHeader("Retry-After",failure.retryAfter().toString());
        mapper.writeValue(response.getOutputStream(),new ApiError(failure.code(),message(failure.code()),id,List.of(),failure.retryable()));
    }
    private String message(String code){return switch(code){case "VALIDATION_FAILED"->"请求格式不符合契约";case "SIGNATURE_INVALID"->"请求签名无效";case "TIMESTAMP_EXPIRED"->"请求时间戳已过期";case "NONCE_REPLAYED"->"请求 nonce 已使用";case "APPLICATION_INACTIVE"->"应用当前不可用";case "ENVIRONMENT_MISMATCH"->"凭证环境不匹配";case "PERMISSION_DENIED"->"应用未获得接口权限";case "RATE_LIMITED"->"请求频率超过限制";default->"服务暂时不可用";};}
    private record Endpoint(String key,PermissionCode permission){}
}
