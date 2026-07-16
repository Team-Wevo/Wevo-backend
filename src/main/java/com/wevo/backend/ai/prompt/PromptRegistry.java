package com.wevo.backend.ai.prompt;

import com.wevo.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PromptRegistry {

    private final Map<PromptTemplateId, PromptDefinition> definitions;

    public PromptRegistry(PromptResourceLoader resourceLoader) {
        try {
            this.definitions = resourceLoader.loadAll().stream()
                    .collect(Collectors.toUnmodifiableMap(PromptDefinition::id, Function.identity()));
        } catch (IllegalStateException exception) {
            throw new PromptException(ErrorCode.AI_PROMPT_INVALID);
        }
    }

    public PromptDefinition get(PromptTemplateId id) {
        PromptDefinition definition = definitions.get(id);
        if (definition == null) {
            throw new PromptException(ErrorCode.AI_PROMPT_NOT_FOUND);
        }
        return definition;
    }
}
