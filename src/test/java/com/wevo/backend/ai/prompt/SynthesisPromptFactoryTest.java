package com.wevo.backend.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.context.AiGapAnswerContext;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.AiOpinionContext;
import com.wevo.backend.ai.service.IssueDetectionOutputValidator;
import com.wevo.backend.ai.service.SynthesisAiOutput;
import com.wevo.backend.ai.service.SynthesisOutputDefinition;
import com.wevo.backend.ai.service.SynthesisOutputValidator;
import com.wevo.backend.ai.service.SynthesisPromptContext;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SynthesisPromptFactoryTest {

    private final SynthesisPromptFactory factory = new SynthesisPromptFactory(
            new PromptRegistry(new PromptResourceLoader()),
            new PromptRenderer(),
            new AiInputSnapshotHasher(),
            new SynthesisOutputDefinition(
                    new SynthesisOutputValidator(new IssueDetectionOutputValidator()))
    );

    @Test
    void latestCandidateSeparatesOpinionIdsFromGapReferenceIds() {
        StructuredAiProviderRequest<SynthesisAiOutput> request =
                factory.providerRequest(context(), Set.of(1L));

        assertThat(request.prompt().trackingVersion()).isEqualTo("opinion-synthesis:v3");
        assertThat(request.prompt().systemPrompt())
                .contains("gapAnswers[].sourceIssueId")
                .contains("gapAnswers[].answerId")
                .contains("opinion id 배열에 절대 넣지 않습니다");
        assertThat(request.prompt().userPrompt())
                .contains("허용되는 opinion id는 opinions[].opinionId뿐입니다")
                .contains("&quot;sourceIssueId&quot;:20")
                .contains("&quot;answerId&quot;:30");
        assertThat(request.validationContext().allowedResourceIds()).containsExactly(1L);
    }

    @Test
    void productionSnapshotCanStillRenderApprovedV2Prompt() {
        StructuredAiProviderRequest<SynthesisAiOutput> request =
                factory.providerRequest(context(), Set.of(1L), "opinion-synthesis:v2");

        assertThat(request.prompt().trackingVersion()).isEqualTo("opinion-synthesis:v2");
    }

    private SynthesisPromptContext context() {
        return new SynthesisPromptContext(
                SynthesisPromptContext.PARTIAL,
                List.of(new AiOpinionContext(
                        1L, "member-1", "제출 의견", "2026-08-11T10:00:00")),
                List.of(new AiGapAnswerContext(20L, 30L, "보충 답변", null)),
                List.of()
        );
    }
}
