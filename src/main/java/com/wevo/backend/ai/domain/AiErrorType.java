package com.wevo.backend.ai.domain;

import com.wevo.backend.global.exception.ErrorCode;

public enum AiErrorType {
    INVALID_REQUEST,
    PROVIDER_AUTHENTICATION,
    PROVIDER_PERMISSION,
    RATE_LIMITED,
    PROVIDER_TIMEOUT,
    PROVIDER_OVERLOADED,
    MODEL_NOT_AVAILABLE,
    PROVIDER_UNAVAILABLE,
    INVALID_RESPONSE,
    PROVIDER_REFUSAL,
    PROVIDER_MAX_TOKENS,
    JSON_PARSE_FAILED,
    SCHEMA_VALIDATION_FAILED,
    TYPE_CONVERSION_FAILED,
    SEMANTIC_VALIDATION_FAILED,
    INPUT_BUDGET_EXCEEDED,
    STALE_INPUT,
    APPLICATION_TIMEOUT,
    WORKER_HEARTBEAT_TIMEOUT,
    PROVIDER_ERROR,
    INTERNAL_ERROR,
    ORPHANED_REQUEST;

    /**
     * 실패한 AI 작업을 조회 응답에 노출할 때 쓰는 외부 실패 코드로 변환한다. (API_SPEC §3.8 —
     * {@code failure.errorCode})
     *
     * <p>이 값은 <b>HTTP 실패 응답의 코드가 아니라</b> 성공(200) 응답 안에 담기는 작업 실패 사유다.
     * 클라이언트가 원인별로 분기할 필요가 없는 인프라·내부 오류는 전용 코드를 늘리지 않고
     * {@link ErrorCode#AI_PROVIDER_ERROR}(AI999)로 모은다. (CLAUDE.md §5.8)
     */
    public ErrorCode toErrorCode() {
        return switch (this) {
            case INVALID_REQUEST -> ErrorCode.AI_INVALID_REQUEST;
            case PROVIDER_AUTHENTICATION -> ErrorCode.AI_PROVIDER_AUTHENTICATION_FAILED;
            case PROVIDER_PERMISSION -> ErrorCode.AI_PROVIDER_PERMISSION_DENIED;
            case RATE_LIMITED -> ErrorCode.AI_RATE_LIMITED;
            case PROVIDER_TIMEOUT -> ErrorCode.AI_PROVIDER_TIMEOUT;
            case PROVIDER_OVERLOADED -> ErrorCode.AI_PROVIDER_OVERLOADED;
            case MODEL_NOT_AVAILABLE -> ErrorCode.AI_MODEL_NOT_AVAILABLE;
            case PROVIDER_UNAVAILABLE -> ErrorCode.AI_PROVIDER_UNAVAILABLE;
            case INVALID_RESPONSE -> ErrorCode.AI_PROVIDER_INVALID_RESPONSE;
            case PROVIDER_REFUSAL -> ErrorCode.AI_PROVIDER_REFUSAL;
            case PROVIDER_MAX_TOKENS -> ErrorCode.AI_PROVIDER_MAX_TOKENS;
            case JSON_PARSE_FAILED -> ErrorCode.AI_STRUCTURED_OUTPUT_JSON_PARSE_FAILED;
            case SCHEMA_VALIDATION_FAILED -> ErrorCode.AI_STRUCTURED_OUTPUT_SCHEMA_VALIDATION_FAILED;
            case TYPE_CONVERSION_FAILED -> ErrorCode.AI_STRUCTURED_OUTPUT_CONVERSION_FAILED;
            case SEMANTIC_VALIDATION_FAILED -> ErrorCode.AI_STRUCTURED_OUTPUT_SEMANTIC_VALIDATION_FAILED;
            case INPUT_BUDGET_EXCEEDED -> ErrorCode.AI_INPUT_BUDGET_EXCEEDED;
            case STALE_INPUT -> ErrorCode.AI_JOB_INPUT_CHANGED;
            case APPLICATION_TIMEOUT, WORKER_HEARTBEAT_TIMEOUT, ORPHANED_REQUEST,
                 PROVIDER_ERROR, INTERNAL_ERROR -> ErrorCode.AI_PROVIDER_ERROR;
        };
    }
}
