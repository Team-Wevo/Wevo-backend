package com.wevo.backend.ai.client;

import com.wevo.backend.ai.config.OpenAiPromptCacheProperties;
import org.springframework.stereotype.Component;

/** Provider 중립 요청을 OpenAI cache 정책으로 변환하되 동적 payload는 key에 사용하지 않는다. */
@Component
public class OpenAiPromptCachePolicy {

    private final OpenAiPromptCacheProperties properties;
    private final OpenAiPromptCacheNamespace namespace;

    public OpenAiPromptCachePolicy(
            OpenAiPromptCacheProperties properties,
            OpenAiPromptCacheNamespace namespace
    ) {
        this.properties = properties;
        this.namespace = namespace;
    }

    OpenAiPromptCacheDecision decide(
            StructuredAiProviderRequest<?> request,
            String modelId
    ) {
        if (!properties.isOptimizationEnabled()) {
            return OpenAiPromptCacheDecision.disabled();
        }

        boolean explicit = properties.requestsExplicit(request.feature());
        if (explicit && !properties.supportsExplicitModel(modelId)) {
            throw new IllegalStateException(
                    "명시적 prompt caching이 검증되지 않은 OpenAI model에는 요청을 보낼 수 없습니다."
            );
        }
        return new OpenAiPromptCacheDecision(
                true,
                namespace.create(
                        request.feature(), modelId, request.prompt(), request.outputDefinition()),
                explicit,
                explicit ? properties.ttl() : null
        );
    }
}
