package com.wevo.backend.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 모든 JSON 응답의 공통 래퍼. (CLAUDE.md §5.4)
 *
 * <p>{@code requiredProperties} 는 <b>성공·실패와 무관하게 항상 내려가는 필드</b>만 나열한다.
 * {@code data}(성공 시)·{@code errors}(필드 단위 사유가 있을 때)는 {@code null} 이면 직렬화에서
 * 빠지므로 선택으로 남긴다 — 문서에서 필수로 표시하면 FE 가 생성한 타입이 실제 응답과 어긋난다.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"success", "code", "message", "timestamp"})
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
