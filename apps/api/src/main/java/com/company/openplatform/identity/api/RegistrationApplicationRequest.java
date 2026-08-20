package com.company.openplatform.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegistrationApplicationRequest(
        @NotBlank @CodePointSize(min = 2, max = 200) String enterpriseName,
        @NotBlank @CodePointSize(min = 2, max = 100) String contactName,
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String contactMobile,
        @NotBlank @CodePointSize(min = 4, max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String username,
        @NotBlank @CodePointSize(min = 12, max = 128) String password) {
    public RegistrationApplicationRequest {
        enterpriseName = normalizeText(enterpriseName);
        contactName = normalizeText(contactName);
        contactMobile = contactMobile == null ? null : contactMobile.replaceAll("[\\s\\p{Z}-]", "");
        username = normalizeText(username);
    }

    private static String normalizeText(String value) {
        return value == null ? null : value.trim();
    }
}
