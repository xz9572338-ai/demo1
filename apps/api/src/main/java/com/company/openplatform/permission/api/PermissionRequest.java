package com.company.openplatform.permission.api;
import com.company.openplatform.permission.domain.PermissionCode; import jakarta.validation.constraints.*; import java.util.List;
public record PermissionRequest(@NotEmpty @Size(max=3) List<@NotNull PermissionCode> permissions,@NotBlank @CodePointSize(min=1,max=500) String reason){}
