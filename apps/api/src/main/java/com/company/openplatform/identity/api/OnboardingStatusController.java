package com.company.openplatform.identity.api;

import com.company.openplatform.identity.application.AccountPrincipal;
import com.company.openplatform.identity.application.GetOnboardingStatusUseCase;
import com.company.openplatform.identity.domain.InvalidCredentialsException;
import com.company.openplatform.shared.observability.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/console/api/v1/onboarding")
public final class OnboardingStatusController {
    private final GetOnboardingStatusUseCase useCase;
    public OnboardingStatusController(GetOnboardingStatusUseCase useCase) { this.useCase = useCase; }
    @GetMapping("/status") OnboardingStatusResponse status(Authentication authentication, HttpServletRequest request) {
        if (!(authentication.getPrincipal() instanceof AccountPrincipal principal)) throw new InvalidCredentialsException();
        var result = useCase.execute(principal.accountId());
        String next = switch (result.applicationStatus()) {
            case PENDING_REVIEW -> "等待商务专员审核";
            case REJECTED -> "通过企业微信或邮件联系技术对接负责人线下处理";
            case APPROVED -> "进入平台创建应用";
        };
        return new OnboardingStatusResponse(result.applicationStatus().name(), result.submittedAt(), result.updatedAt(),
                result.rejectionReason(), "商务专员", List.of("企业微信", "邮件"), next,
                (String) request.getAttribute(RequestIdFilter.ATTRIBUTE));
    }
}
