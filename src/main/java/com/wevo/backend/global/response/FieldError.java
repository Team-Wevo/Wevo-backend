package com.wevo.backend.global.response;

import lombok.Getter;

@Getter
public class FieldError {

    private final String field;
    private final String reason;

    public FieldError(String field, String reason) {
        this.field = field;
        this.reason = reason;
    }
}
