package com.wevo.backend.ai.dto.response;

import java.util.UUID;

/**
 * AI 의견 정리 실행 접수 응답. 폴링에 사용할 작업 {@code requestId}만 반환한다. (§3.8.1)
 *
 * @param requestId 비동기 AI 작업 요청 ID
 */
public record SynthesisAcceptedResponse(UUID requestId) {
}
