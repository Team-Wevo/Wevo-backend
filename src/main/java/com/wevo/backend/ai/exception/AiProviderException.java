package com.wevo.backend.ai.exception;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import lombok.Getter;

import java.time.Duration;

/**
 * AI Provider 호출 실패를 공통 API 오류로 전달하는 예외.
 */
@Getter
public class AiProviderException extends BusinessException {

    private final AiUsageMetadata usageMetadata;
    private final int attemptCount;
    private final Duration retryAfter;

    public AiProviderException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, cause, null, 0, null);
    }

    public AiProviderException(
            ErrorCode errorCode,
            Throwable cause,
            AiUsageMetadata usageMetadata,
            int attemptCount
    ) {
        this(errorCode, cause, usageMetadata, attemptCount, null);
    }

    public AiProviderException(
            ErrorCode errorCode,
            Throwable cause,
            AiUsageMetadata usageMetadata,
            int attemptCount,
            Duration retryAfter
    ) {
        super(errorCode);
        this.usageMetadata = usageMetadata;
        this.attemptCount = attemptCount;
        this.retryAfter = retryAfter;
        initCause(cause);
    }

    public AiProviderException withAttemptContext(int attempts) {
        return new AiProviderException(getErrorCode(), getCause(), usageMetadata, attempts, retryAfter);
    }
}
