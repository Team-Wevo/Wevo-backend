package com.wevo.backend.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 공백을 제외한 UTF-16 문자 수의 최대값을 검증한다. {@code null}은 유효한 값으로 취급한다.
 */
@Documented
@Constraint(validatedBy = NonWhitespaceSizeValidator.class)
@Target({
        ElementType.FIELD,
        ElementType.METHOD,
        ElementType.PARAMETER,
        ElementType.ANNOTATION_TYPE,
        ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface NonWhitespaceSize {

    String message() default "공백을 제외한 문자 수는 {max}자 이하여야 합니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int max();
}
