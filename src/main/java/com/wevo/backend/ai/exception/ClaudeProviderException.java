package com.wevo.backend.ai.exception;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;

/**
 * Claude 제공자 호출 실패를 공통 API 오류로 전달하는 예외.
 */
public class ClaudeProviderException extends BusinessException {

    public ClaudeProviderException(ErrorCode errorCode, Throwable cause) {
        super(errorCode);
        initCause(cause);
    }
}
