package com.company.openplatform.identity.application;

public record SubmitRegistrationCommand(
        String enterpriseName, String contactName, String contactMobile, String username, String password) {}
