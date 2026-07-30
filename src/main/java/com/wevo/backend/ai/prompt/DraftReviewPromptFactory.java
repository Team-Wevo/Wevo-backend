package com.wevo.backend.ai.prompt;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.DraftReviewContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.dto.model.DraftReviewOutput;
import com.wevo.backend.ai.service.DraftReviewOutputDefinition;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 현재 초안·직접 상위 본문·template guide를 분리된 data block으로 렌더링한다. */
@Component
public class DraftReviewPromptFactory {

    public static final PromptTemplateId PROMPT_ID =
            new PromptTemplateId("draft-review", 1);

    private final PromptRegistry promptRegistry;
    private final PromptRenderer promptRenderer;
    private final AiInputSnapshotHasher snapshotHasher;
    private final DraftReviewOutputDefinition outputDefinition;

    public DraftReviewPromptFactory(
            PromptRegistry promptRegistry,
            PromptRenderer promptRenderer,
            AiInputSnapshotHasher snapshotHasher,
            DraftReviewOutputDefinition outputDefinition
    ) {
        this.promptRegistry = promptRegistry;
        this.promptRenderer = promptRenderer;
        this.snapshotHasher = snapshotHasher;
        this.outputDefinition = outputDefinition;
    }

    public StructuredAiProviderRequest<DraftReviewOutput> providerRequest(
            DraftReviewContext context
    ) {
        if (context == null) {
            throw new IllegalArgumentException("사전 검토 context는 필수입니다.");
        }
        RenderedPrompt prompt = promptRenderer.render(
                promptRegistry.get(PROMPT_ID),
                Map.of(
                        "currentDraft", serialize(new CurrentDraftData(
                                context.project(),
                                context.section().title(),
                                context.section().sectionOrder(),
                                context.contentVersion(),
                                context.content())),
                        "prerequisites", serialize(context.prerequisites()),
                        "templateGuide", templateGuide(context)
                )
        );
        Set<String> sourceContents = new LinkedHashSet<>();
        sourceContents.add(context.content());
        context.prerequisites().forEach(item -> sourceContents.add(item.content()));
        return new StructuredAiProviderRequest<>(
                AiFeature.DRAFT_REVIEW,
                prompt,
                outputDefinition.get(),
                StructuredOutputValidationContext.forSourceContents(
                        context.content(), sourceContents)
        );
    }

    private String templateGuide(DraftReviewContext context) {
        String guide = context.section().template().guide();
        return guide == null || guide.isBlank() ? "(제공되지 않음)" : guide;
    }

    private String serialize(Object value) {
        return new String(
                snapshotHasher.canonicalSnapshot(value).canonicalBytes(),
                StandardCharsets.UTF_8
        );
    }

    private record CurrentDraftData(
            Object project,
            String sectionTitle,
            int sectionOrder,
            int contentVersion,
            String content
    ) {
    }
}
