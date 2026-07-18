package com.wevo.backend.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.global.exception.ErrorCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    // 시간 계약은 KST 고정 (CLAUDE.md §5.4) — JVM 기본 시간대에 의존하지 않는다
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;                 // 성공 시
    private final List<FieldError> errors; // 실패 시
    private final LocalDateTime timestamp;

    private ApiResponse(boolean success, String code, String message,
                        T data, List<FieldError> errors) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.errors = errors;
        this.timestamp = LocalDateTime.now(KST);
    }

    public static <T> ApiResponse<T> success(String code, String message, T data) {
        return new ApiResponse<>(true, code, message, data, null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, List<FieldError> errors) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null, errors);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return error(errorCode, null);
    }
}
