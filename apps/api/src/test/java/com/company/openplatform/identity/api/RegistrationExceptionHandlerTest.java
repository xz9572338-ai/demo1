package com.company.openplatform.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class RegistrationExceptionHandlerTest {
    @Test
    void mapsUnexpectedFailureToStable500WithoutLeakingCause(CapturedOutput output) {
        var request = new MockHttpServletRequest();
        request.setAttribute(com.company.openplatform.shared.observability.RequestIdFilter.ATTRIBUTE, "req_test");

        var response = new RegistrationExceptionHandler().unexpected(
                new RuntimeException("secret database detail"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().code()).isEqualTo("REGISTRATION_FAILED");
        assertThat(response.getBody().message()).doesNotContain("secret");
        assertThat(response.getBody().retryable()).isTrue();
        assertThat(output.getAll()).doesNotContain("secret database detail").doesNotContain("at com.company");
    }
}
