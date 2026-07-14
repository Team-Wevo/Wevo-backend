package com.wevo.backend.ai.client;

@FunctionalInterface
public interface StructuredOutputValidator<T> {

    void validate(T output, StructuredOutputValidationContext context);

    static <T> StructuredOutputValidator<T> noOp() {
        return (output, context) -> {
        };
    }
}
