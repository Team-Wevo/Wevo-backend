package com.wevo.backend.ai.exception;

import com.wevo.backend.global.exception.ErrorCode;

/** AI Provider가 구성되지 않아 새 실행을 시작할 수 없음을 나타내는 안전한 예외. */
public class AiProviderUnavailableException extends AiProviderException {

    public AiProviderUnavailableException() {
        super(ErrorCode.AI_PROVIDER_UNAVAILABLE, null);
    }
}
