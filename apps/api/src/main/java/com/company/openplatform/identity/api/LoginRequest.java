package com.company.openplatform.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(min = 4, max = 64) String login,
                           @NotBlank @CodePointSize(min = 12, max = 128) String password) {}
