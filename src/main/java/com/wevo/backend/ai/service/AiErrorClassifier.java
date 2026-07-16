package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.exception.ClaudeProviderException;
import com.wevo.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class AiErrorClassifier {

    public AiErrorType classify(Throwable throwable) {
        if (!(throwable instanceof ClaudeProviderException exception)) {
            return AiErrorType.INTERNAL_ERROR;
        }

        ErrorCode errorCode = exception.getErrorCode();
        return switch (errorCode) {
            case AI_INVALID_REQUEST -> AiErrorType.INVALID_REQUEST;
            case AI_PROVIDER_AUTHENTICATION_FAILED -> AiErrorType.PROVIDER_AUTHENTICATION;
            case AI_PROVIDER_PERMISSION_DENIED -> AiErrorType.PROVIDER_PERMISSION;
            case AI_RATE_LIMITED -> AiErrorType.RATE_LIMITED;
            case AI_PROVIDER_TIMEOUT -> AiErrorType.PROVIDER_TIMEOUT;
            case AI_PROVIDER_OVERLOADED -> AiErrorType.PROVIDER_OVERLOADED;
            case AI_MODEL_NOT_AVAILABLE -> AiErrorType.MODEL_NOT_AVAILABLE;
            case AI_PROVIDER_UNAVAILABLE -> AiErrorType.PROVIDER_UNAVAILABLE;
            case AI_PROVIDER_INVALID_RESPONSE -> AiErrorType.INVALID_RESPONSE;
            case AI_PROVIDER_REFUSAL -> AiErrorType.PROVIDER_REFUSAL;
            case AI_PROVIDER_MAX_TOKENS -> AiErrorType.PROVIDER_MAX_TOKENS;
            case AI_STRUCTURED_OUTPUT_JSON_PARSE_FAILED -> AiErrorType.JSON_PARSE_FAILED;
            case AI_STRUCTURED_OUTPUT_SCHEMA_VALIDATION_FAILED -> AiErrorType.SCHEMA_VALIDATION_FAILED;
            case AI_STRUCTURED_OUTPUT_CONVERSION_FAILED -> AiErrorType.TYPE_CONVERSION_FAILED;
            case AI_STRUCTURED_OUTPUT_SEMANTIC_VALIDATION_FAILED -> AiErrorType.SEMANTIC_VALIDATION_FAILED;
            default -> AiErrorType.PROVIDER_ERROR;
        };
    }

    public String safeMessage(Throwable throwable) {
        if (throwable instanceof ClaudeProviderException exception) {
            return exception.getErrorCode().getMessage();
        }
        return "예상하지 못한 AI 처리 오류가 발생했습니다.";
    }
}
