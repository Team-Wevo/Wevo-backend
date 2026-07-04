package com.wevo.backend.global.exception;

import com.wevo.backend.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}
