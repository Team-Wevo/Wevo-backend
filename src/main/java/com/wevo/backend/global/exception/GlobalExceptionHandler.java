package com.wevo.backend.global.exception;

import com.wevo.backend.auth.domain.AuthProvider;
import com.wevo.backend.global.response.ApiResponse;
import com.wevo.backend.global.response.FieldError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.List;

/**
 * 모든 REST Controller에서 발생하는 예외를 공통 형식으로 처리.
 *
 * Controller마다 중복된 try-catch 문을 작성하지 않도록 예외 처리를 한곳에 모으고,
 * 실패 응답을 ApiResponse 형식으로 통일.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 서비스 계층에서 발생한 BusinessException을 처리.
     *
     * 예외가 가진 ErrorCode에서 HTTP 상태와 오류 정보를 꺼내
     * 공통 실패 응답으로 변환
     *
     * @param exception 처리할 비즈니스 예외
     * @return HTTP 상태와 공통 실패 응답
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception
    ) {
        // 비즈니스 예외에 담긴 HTTP 상태, 오류 코드, 메시지를 가져옴.
        ErrorCode errorCode = exception.getErrorCode();

        // ErrorCode의 HTTP 상태와 ApiResponse의 공통 실패 본문을 함께 반환.
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode, exception.getErrors()));
    }

    /**
     * {@code @Valid} 검증 실패(MethodArgumentNotValidException)를 처리.
     *
     * 필드별 검증 실패 사유를 공통 실패 응답의 {@code errors}로 변환한다.
     *
     * @param exception 검증 실패 예외
     * @return {@code C001 INVALID_INPUT} 과 필드 단위 오류 목록
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception
    ) {
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new FieldError(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();

        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT, errors));
    }

    /**
     * JSON 본문 파싱 실패를 공통 실패 응답으로 변환한다.
     *
     * <p>{@link AuthProvider} enum 역직렬화 실패는 문서화된 A006으로 내려주고,
     * 그 외 잘못된 JSON/타입 오류는 C001로 반환한다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception
    ) {
        ErrorCode errorCode = isUnsupportedAuthProvider(exception)
                ? ErrorCode.UNSUPPORTED_AUTH_PROVIDER
                : ErrorCode.INVALID_INPUT;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException exception
    ) {
        return ResponseEntity
                .status(ErrorCode.CONFLICT.getStatus())
                .body(ApiResponse.error(ErrorCode.CONFLICT));
    }

    private boolean isUnsupportedAuthProvider(HttpMessageNotReadableException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof InvalidFormatException invalidFormatException
                    && invalidFormatException.getTargetType() == AuthProvider.class) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
