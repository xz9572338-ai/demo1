package com.company.openplatform.identity.api;

import com.company.openplatform.identity.application.SubmitRegistrationApplicationUseCase;
import com.company.openplatform.identity.application.SubmitRegistrationCommand;
import com.company.openplatform.shared.observability.RequestIdFilter;
import com.company.openplatform.shared.security.RegistrationRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/console/api/v1/registration-applications")
public class RegistrationApplicationController {
    private static final Logger log = LoggerFactory.getLogger(RegistrationApplicationController.class);
    private final SubmitRegistrationApplicationUseCase useCase;
    private final RegistrationRateLimiter rateLimiter;

    public RegistrationApplicationController(SubmitRegistrationApplicationUseCase useCase,
            RegistrationRateLimiter rateLimiter) {
        this.useCase = useCase;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping
    public ResponseEntity<RegistrationApplicationResponse> submit(
            @Valid @RequestBody RegistrationApplicationRequest request, HttpServletRequest servletRequest) {
        rateLimiter.consumeBusiness(servletRequest);
        var result = useCase.submit(new SubmitRegistrationCommand(
                request.enterpriseName(), request.contactName(), request.contactMobile(),
                request.username(), request.password()));
        String requestId = (String) servletRequest.getAttribute(RequestIdFilter.ATTRIBUTE);
        log.info("registration_submitted requestId={} status=PENDING_REVIEW", requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(new RegistrationApplicationResponse(
                result.applicationId(), result.status(), result.submittedAt(), "商务专员",
                List.of("企业微信", "邮件"), "等待商务专员审核，审核前无法使用平台功能", requestId));
    }

    public record CsrfResponse(String headerName, String token) {}
}
