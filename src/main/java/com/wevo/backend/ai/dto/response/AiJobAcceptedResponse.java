package com.wevo.backend.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** 비동기 AI 실행 요청의 polling 식별자. */
@Schema(requiredProperties = {"requestId"})
public record AiJobAcceptedResponse(UUID requestId) {
}
