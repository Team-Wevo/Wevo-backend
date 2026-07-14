package com.wevo.backend.ai.prompt;

import com.wevo.backend.global.exception.ErrorCode;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PromptResourceLoader {

    private static final String RESOURCE_PATTERN = "classpath*:prompts/ai/*/v*/*.md";
    private static final Pattern RESOURCE_PATH_PATTERN = Pattern.compile(
            ".*/prompts/ai/([a-z][a-z0-9]*(?:-[a-z0-9]+)*)/v([1-9][0-9]*)/(system|user)\\.md$"
    );

    private final ResourcePatternResolver resourceResolver;

    public PromptResourceLoader() {
        this(new PathMatchingResourcePatternResolver());
    }

    PromptResourceLoader(ResourcePatternResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    public List<PromptDefinition> loadAll() {
        try {
            Map<PromptTemplateId, Map<String, Resource>> resourcesById = new HashMap<>();
            for (Resource resource : resourceResolver.getResources(RESOURCE_PATTERN)) {
                ResourceDescriptor descriptor = describe(resource);
                Resource previous = resourcesById
                        .computeIfAbsent(descriptor.id(), ignored -> new HashMap<>())
                        .put(descriptor.kind(), resource);
                if (previous != null) {
                    throw invalidPrompt();
                }
            }

            List<PromptDefinition> definitions = new ArrayList<>();
            for (Map.Entry<PromptTemplateId, Map<String, Resource>> entry : resourcesById.entrySet()) {
                Map<String, Resource> pair = entry.getValue();
                if (!pair.keySet().equals(Set.of("system", "user"))) {
                    throw invalidPrompt();
                }
                definitions.add(toDefinition(entry.getKey(), pair));
            }
            definitions.sort((left, right) -> left.id().trackingValue().compareTo(right.id().trackingValue()));
            return List.copyOf(definitions);
        } catch (IOException | IllegalArgumentException exception) {
            throw invalidPrompt();
        }
    }

    private PromptDefinition toDefinition(PromptTemplateId id, Map<String, Resource> pair) throws IOException {
        String systemTemplate = pair.get("system").getContentAsString(StandardCharsets.UTF_8);
        String userTemplate = pair.get("user").getContentAsString(StandardCharsets.UTF_8);
        if (systemTemplate.isBlank() || userTemplate.isBlank()) {
            throw invalidPrompt();
        }

        Set<String> variables = new LinkedHashSet<>(PromptTemplateParser.extractVariables(systemTemplate));
        variables.addAll(PromptTemplateParser.extractVariables(userTemplate));
        return new PromptDefinition(id, systemTemplate, userTemplate, variables);
    }

    private ResourceDescriptor describe(Resource resource) throws IOException {
        String location = resource.getURI().toString();
        Matcher matcher = RESOURCE_PATH_PATTERN.matcher(location);
        if (!matcher.matches()) {
            throw invalidPrompt();
        }
        return new ResourceDescriptor(
                new PromptTemplateId(matcher.group(1), Integer.parseInt(matcher.group(2))),
                matcher.group(3)
        );
    }

    private PromptException invalidPrompt() {
        return new PromptException(ErrorCode.AI_PROMPT_INVALID);
    }

    private record ResourceDescriptor(PromptTemplateId id, String kind) {
    }
}
