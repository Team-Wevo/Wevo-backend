package com.wevo.backend.global.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 검증 실패 등 필드 단위 사유. 두 값 모두 생성자에서 채워지므로 항상 내려간다. (CLAUDE.md §5.4) */
@Getter
@Schema(requiredProperties = {"field", "reason"})
public class FieldError {

    private final String field;
    private final String reason;

    public FieldError(String field, String reason) {
        this.field = field;
        this.reason = reason;
    }
}
