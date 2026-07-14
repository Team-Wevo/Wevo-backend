package com.wevo.backend.ai.client;

record StructuredConversionResult<T>(T value, StructuredConversionFailure failure) {

    static <T> StructuredConversionResult<T> success(T value) {
        return new StructuredConversionResult<>(value, null);
    }

    static <T> StructuredConversionResult<T> failure(StructuredConversionFailure failure) {
        return new StructuredConversionResult<>(null, failure);
    }

    boolean isSuccess() {
        return value != null && failure == null;
    }
}
