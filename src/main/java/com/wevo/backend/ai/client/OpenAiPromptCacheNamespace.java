package com.wevo.backend.ai.client;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.prompt.RenderedPrompt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 사용자·프로젝트 식별자나 원문을 노출하지 않는 안정 prefix cache namespace 생성기. */
@Component
public class OpenAiPromptCacheNamespace {

    private static final String KEY_PREFIX = "wv:";
    private static final int MAX_KEY_LENGTH = 64;

    public String create(
            AiFeature feature,
            String modelId,
            RenderedPrompt prompt,
            StructuredOutputDefinition<?> outputDefinition
    ) {
        if (feature == null || modelId == null || modelId.isBlank()
                || prompt == null || outputDefinition == null) {
            throw new IllegalArgumentException(
                    "cache namespace에는 feature, model, prompt, outputDefinition이 필요합니다."
            );
        }
        String material = String.join("\n",
                "namespace:v1",
                feature.configKey(),
                modelId,
                prompt.trackingVersion(),
                outputDefinition.schemaId().trackingValue(),
                digest(prompt.systemPrompt()),
                digest(outputDefinition.jsonSchema())
        );
        String key = KEY_PREFIX + digest(material);
        return key.substring(0, Math.min(MAX_KEY_LENGTH, key.length()));
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
