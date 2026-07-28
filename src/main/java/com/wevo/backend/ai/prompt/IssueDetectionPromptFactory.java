package com.wevo.backend.ai.prompt;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.client.StructuredPromptFormatter;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.AiOpinionContext;
import com.wevo.backend.ai.context.AiTokenBudgetInput;
import com.wevo.backend.ai.context.IssueDetectionContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.dto.model.IssueDetectionOutput;
import com.wevo.backend.ai.service.IssueDetectionOutputDefinition;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 쟁점 감지 context를 버전형 prompt와 구조화 출력 요청으로 변환한다. */
@Component
public class IssueDetectionPromptFactory {

    public static final PromptTemplateId PROMPT_ID = new PromptTemplateId("issue-detection", 1);

    private final PromptRegistry promptRegistry;
    private final PromptRenderer promptRenderer;
    private final AiInputSnapshotHasher snapshotHasher;
    private final IssueDetectionOutputDefinition outputDefinition;

    public IssueDetectionPromptFactory(
            PromptRegistry promptRegistry,
            PromptRenderer promptRenderer,
            AiInputSnapshotHasher snapshotHasher,
            IssueDetectionOutputDefinition outputDefinition
    ) {
        this.promptRegistry = promptRegistry;
        this.promptRenderer = promptRenderer;
        this.snapshotHasher = snapshotHasher;
        this.outputDefinition = outputDefinition;
    }

    public StructuredAiProviderRequest<IssueDetectionOutput> providerRequest(
            IssueDetectionContext context
    ) {
        Set<Long> allowedOpinionIds = allowedOpinionIds(context);
        RenderedPrompt prompt = promptRenderer.render(
                promptRegistry.get(PROMPT_ID),
                Map.of("issueDetectionContext", serialize(context))
        );
        return new StructuredAiProviderRequest<>(
                AiFeature.ISSUE_DETECTION,
                prompt,
                outputDefinition.get(),
                new StructuredOutputValidationContext(allowedOpinionIds)
        );
    }

    public AiTokenBudgetInput tokenBudgetInput(IssueDetectionContext context) {
        StructuredAiProviderRequest<IssueDetectionOutput> request = providerRequest(context);
        return AiTokenBudgetInput.of(
                request.prompt().systemPrompt(),
                StructuredPromptFormatter.initialUserPrompt(request)
        );
    }

    private Set<Long> allowedOpinionIds(IssueDetectionContext context) {
        if (context == null || context.opinions() == null) {
            throw new IllegalArgumentException("issue detection context와 의견 목록은 필수입니다.");
        }
        Set<Long> opinionIds = new HashSet<>();
        for (AiOpinionContext opinion : context.opinions()) {
            if (opinion == null || opinion.opinionId() == null || !opinionIds.add(opinion.opinionId())) {
                throw new IllegalArgumentException("issue detection opinion ID는 null 또는 중복일 수 없습니다.");
            }
        }
        return Set.copyOf(opinionIds);
    }

    private String serialize(IssueDetectionContext context) {
        return new String(
                snapshotHasher.canonicalSnapshot(context).canonicalBytes(),
                StandardCharsets.UTF_8
        );
    }
}
