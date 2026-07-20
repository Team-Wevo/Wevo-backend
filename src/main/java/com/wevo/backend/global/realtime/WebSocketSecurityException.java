package com.wevo.backend.global.realtime;

import com.wevo.backend.global.exception.ErrorCode;
import org.springframework.messaging.MessagingException;

/**
 * STOMP 인증·구독 인가 실패를 안전한 외부 오류 코드와 메시지로 전달한다.
 */
public class WebSocketSecurityException extends MessagingException {

    private final ErrorCode errorCode;

    /**
     * 내부 예외 원문 대신 외부 노출이 허용된 공통 오류 메시지만 MessagingException에 담는다.
     */
    public WebSocketSecurityException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 연결·구독 실패 원인을 STOMP 오류 처리 계층에서 식별할 수 있게 반환한다. */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
