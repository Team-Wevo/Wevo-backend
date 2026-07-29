package com.wevo.backend.ai.dto.response;

import java.util.UUID;

/** 비동기 AI 실행 요청의 polling 식별자. */
public record AiJobAcceptedResponse(UUID requestId) {
}
