package com.company.openplatform.permission.api;
import jakarta.validation.Constraint; import jakarta.validation.Payload; import java.lang.annotation.*;
@Documented @Constraint(validatedBy=CodePointSizeValidator.class) @Target({ElementType.FIELD,ElementType.PARAMETER,ElementType.RECORD_COMPONENT}) @Retention(RetentionPolicy.RUNTIME)
public @interface CodePointSize { String message() default "size invalid"; int min() default 0; int max() default Integer.MAX_VALUE; Class<?>[] groups() default {}; Class<? extends Payload>[] payload() default {}; }
