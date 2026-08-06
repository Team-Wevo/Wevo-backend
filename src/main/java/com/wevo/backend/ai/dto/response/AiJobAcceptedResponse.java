package com.wevo.backend.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** 비동기 AI 실행 요청의 polling 식별자. */
@Schema(requiredProperties = {"requestId"},
        example = """
                {
                  "requestId": "0f7c1c1e-6a1f-4c39-9a1e-2b7c9d4e5f60"
                }""")
public record AiJobAcceptedResponse(UUID requestId) {
}
