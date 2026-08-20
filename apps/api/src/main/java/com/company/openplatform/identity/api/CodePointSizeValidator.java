package com.company.openplatform.identity.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class CodePointSizeValidator implements ConstraintValidator<CodePointSize, String> {
    private int min;
    private int max;

    @Override public void initialize(CodePointSize annotation) {
        min = annotation.min();
        max = annotation.max();
    }

    @Override public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        int length = value.codePointCount(0, value.length());
        return length >= min && length <= max;
    }
}
