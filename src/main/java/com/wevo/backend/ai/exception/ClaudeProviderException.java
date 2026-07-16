package com.wevo.backend.ai.exception;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import lombok.Getter;

/**
 * Claude 제공자 호출 실패를 공통 API 오류로 전달하는 예외.
 */
@Getter
public class ClaudeProviderException extends BusinessException {

    private final AiUsageMetadata usageMetadata;
    private final int attemptCount;

    public ClaudeProviderException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, cause, null, 0);
    }

    public ClaudeProviderException(
            ErrorCode errorCode,
            Throwable cause,
            AiUsageMetadata usageMetadata,
            int attemptCount
    ) {
        super(errorCode);
        this.usageMetadata = usageMetadata;
        this.attemptCount = attemptCount;
        initCause(cause);
    }

    public ClaudeProviderException withAttemptContext(int attempts) {
        return new ClaudeProviderException(getErrorCode(), getCause(), usageMetadata, attempts);
    }
}
