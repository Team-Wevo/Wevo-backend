package com.wevo.backend.ai.context;

import com.wevo.backend.ai.domain.AiFeature;

/**
 * 외부 API DTO나 JPA entity와 분리된 Provider 중립 AI 입력 모델.
 */
public interface AiFeatureContext {

    AiFeature feature();

    /**
     * {@code AiJob.sourceVersion}에 저장할 100자 이하의 기능별 입력 버전 표현.
     */
    String sourceVersion();
}
