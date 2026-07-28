package com.wevo.backend.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NonWhitespaceSizeValidator
        implements ConstraintValidator<NonWhitespaceSize, CharSequence> {

    private int max;

    @Override
    public void initialize(NonWhitespaceSize constraintAnnotation) {
        this.max = constraintAnnotation.max();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        return !TextLengthPolicy.exceedsNonWhitespaceLimit(value, max);
    }
}
