package com.wevo.backend.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.ai.domain.AiRequestFeature;
import com.wevo.backend.ai.domain.AiRequestStatus;
import java.util.UUID;

/** 결과 본문을 제외한 공통 AI 작업 상태 조회 응답. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiJobResponse(
        UUID requestId,
        AiRequestFeature feature,
        AiRequestStatus status,
        FailureResponse failure
) {

    public record FailureResponse(String errorCode, String message) {
    }
}
