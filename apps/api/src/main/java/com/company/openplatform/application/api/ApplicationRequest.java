package com.company.openplatform.application.api;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size;
public record ApplicationRequest(@NotBlank @Size(max=100) String name,@NotBlank @Size(max=500) String purpose) {}
