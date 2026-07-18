package com.wevo.backend.ai.client;

/**
 * 서비스 계층과 선택된 AI Provider 구현을 분리하는 경계.
 */
public interface AiProviderGateway {

    AiProviderResponse generate(AiProviderRequest request);

    default <T> StructuredAiProviderResponse<T> generateStructured(StructuredAiProviderRequest<T> request) {
        throw new UnsupportedOperationException("구조화 출력 호출을 지원하지 않습니다.");
    }
}
