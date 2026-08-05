package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.exception.AiProviderUnavailableException;
import org.springframework.stereotype.Component;

/** Provider 비활성 상태에서 처리할 수 없는 신규·재시도 AI 작업 생성을 차단한다. */
@Component
public class AiExecutionAvailabilityGuard {

    private final AiProperties properties;

    public AiExecutionAvailabilityGuard(AiProperties properties) {
        this.properties = properties;
    }

    public void requireAvailable() {
        if ("none".equals(properties.provider())) {
            throw new AiProviderUnavailableException();
        }
    }
}
