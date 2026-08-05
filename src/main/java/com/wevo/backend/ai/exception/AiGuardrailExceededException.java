package com.wevo.backend.ai.exception;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;

/** 제품 AI quota 또는 비용 예산을 초과했을 때 안전한 재시도 시각을 전달한다. */
public class AiGuardrailExceededException extends BusinessException {

    private final long retryAfterSeconds;

    public AiGuardrailExceededException(ErrorCode errorCode, long retryAfterSeconds) {
        super(errorCode);
        if (retryAfterSeconds <= 0) {
            throw new IllegalArgumentException("retryAfterSeconds는 1 이상이어야 합니다.");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
