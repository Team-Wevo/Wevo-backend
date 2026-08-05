package com.wevo.backend.ai.prompt;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.DraftGenerationContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.service.DraftGenerationOutput;
import com.wevo.backend.ai.service.DraftGenerationOutputDefinition;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** current synthesis와 template을 초안 생성 prompt 및 evidence allowlist로 변환한다. */
@Component
public class DraftGenerationPromptFactory {

    public static final PromptTemplateId PROMPT_ID =
            new PromptTemplateId("draft-generation", 2);

    private final PromptRegistry promptRegistry;
    private final PromptRenderer promptRenderer;
    private final AiInputSnapshotHasher snapshotHasher;
    private final DraftGenerationOutputDefinition outputDefinition;

    public DraftGenerationPromptFactory(
            PromptRegistry promptRegistry,
            PromptRenderer promptRenderer,
            AiInputSnapshotHasher snapshotHasher,
            DraftGenerationOutputDefinition outputDefinition
    ) {
        this.promptRegistry = promptRegistry;
        this.promptRenderer = promptRenderer;
        this.snapshotHasher = snapshotHasher;
        this.outputDefinition = outputDefinition;
    }

    public StructuredAiProviderRequest<DraftGenerationOutput> providerRequest(
            DraftGenerationContext context
    ) {
        return providerRequest(context, PROMPT_ID.trackingValue());
    }

    public StructuredAiProviderRequest<DraftGenerationOutput> providerRequest(
            DraftGenerationContext context,
            String promptVersion
    ) {
        if (context == null) {
            throw new IllegalArgumentException("초안 생성 context는 필수입니다.");
        }
        RenderedPrompt prompt = promptRenderer.render(
                promptRegistry.get(PROMPT_ID, promptVersion),
                Map.of("draftContext", serialize(context))
        );
        Set<Long> opinionIds = context.synthesis().opinionEvidence().stream()
                .map(evidence -> evidence.opinionId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<Long> issueIds = new HashSet<>();
        context.synthesis().conflictDecisions().forEach(decision -> issueIds.add(decision.issueId()));
        context.synthesis().gapIssues().forEach(gap -> issueIds.add(gap.issueId()));
        context.synthesis().gapAnswers().forEach(answer -> issueIds.add(answer.sourceIssueId()));
        Set<Long> answerIds = context.synthesis().gapAnswers().stream()
                .map(answer -> answer.answerId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<Long> unresolvedGapIds = context.synthesis().gapIssues().stream()
                .filter(gap -> !gap.answered())
                .map(gap -> gap.issueId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        return new StructuredAiProviderRequest<>(
                AiFeature.DRAFT_GENERATION,
                prompt,
                outputDefinition.get(),
                new StructuredOutputValidationContext(
                        opinionIds,
                        Set.copyOf(issueIds),
                        answerIds,
                        unresolvedGapIds
                )
        );
    }

    private String serialize(DraftGenerationContext context) {
        return new String(
                snapshotHasher.canonicalSnapshot(context).canonicalBytes(),
                StandardCharsets.UTF_8
        );
    }
}
