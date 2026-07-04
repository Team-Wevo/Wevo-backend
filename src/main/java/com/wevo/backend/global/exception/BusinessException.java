package com.wevo.backend.global.exception;

import com.wevo.backend.global.response.FieldError;
import lombok.Getter;

import java.util.List;

/**
 * 서비스 로직에서 발생한 비즈니스 오류를 표현하는 공통 예외
 *
 * 발생한 오류의 {@link ErrorCode}를 보관하고,
 * GlobalExceptionHandler가 이를 공통 오류 응답으로 변환할 수 있게 함
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * HTTP 상태, 오류 식별 코드, 사용자 메시지를 포함한 오류 정보.
     */
    private final ErrorCode errorCode;
    private final List<FieldError> errors;

    /**
     * 지정된 오류 코드로 비즈니스 예외를 생성.
     *
     * 오류 메시지를 부모 예외에도 전달하여 로그와 스택 트레이스에서
     * 발생 원인을 확인할 수 있게 함
     *
     * @param errorCode 발생한 비즈니스 오류 정보
     */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, List<FieldError> errors) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.errors = errors == null || errors.isEmpty()
                ? null
                : List.copyOf(errors);
    }
}
