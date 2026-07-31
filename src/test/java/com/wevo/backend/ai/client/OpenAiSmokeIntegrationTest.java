package com.wevo.backend.ai.client;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.AiBaseDraftContext;
import com.wevo.backend.ai.context.AiDraftSynthesisContext;
import com.wevo.backend.ai.context.AiGapIssueContext;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.AiOpinionContext;
import com.wevo.backend.ai.context.AiOpinionEvidenceContext;
import com.wevo.backend.ai.context.AiProjectBrief;
import com.wevo.backend.ai.context.AiProjectIdentity;
import com.wevo.backend.ai.context.AiSectionContext;
import com.wevo.backend.ai.context.AiTemplateContext;
import com.wevo.backend.ai.context.DraftGenerationContext;
import com.wevo.backend.ai.context.DraftReviewContext;
import com.wevo.backend.ai.context.IssueDetectionContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.dto.model.DraftReviewOutput;
import com.wevo.backend.ai.dto.model.IssueDetectionOutput;
import com.wevo.backend.ai.prompt.DraftGenerationPromptFactory;
import com.wevo.backend.ai.prompt.DraftReviewPromptFactory;
import com.wevo.backend.ai.prompt.IssueDetectionPromptFactory;
import com.wevo.backend.ai.prompt.PromptRegistry;
import com.wevo.backend.ai.prompt.PromptRenderer;
import com.wevo.backend.ai.prompt.PromptTemplateId;
import com.wevo.backend.ai.prompt.SynthesisPromptFactory;
import com.wevo.backend.ai.service.DraftGenerationOutput;
import com.wevo.backend.ai.service.SynthesisAiOutput;
import com.wevo.backend.ai.service.SynthesisPromptContext;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("openai-integration")
@SpringBootTest(properties = {
        "wevo.ai.provider=openai",
        "wevo.ai.openai.api-key=${OPENAI_API_KEY}",
        "wevo.ai.openai.base-url=${OPENAI_API_BASE_URL:https://api.openai.com}",
        "wevo.ai.openai.model=${OPENAI_API_MODEL:gpt-5.6-luna}",
        "wevo.ai.openai.timeout=${OPENAI_API_TIMEOUT:60s}",
        "wevo.ai.openai.max-output-tokens=1024",
        "wevo.ai.openai.reasoning-effort=${OPENAI_API_REASONING_EFFORT:medium}",
        "wevo.ai.default-options.max-retries=0",
        "wevo.ai.structured-output.max-correction-retries=0"
})
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OPENAI_INTEGRATION_ENABLED", matches = "(?i)true")
class OpenAiSmokeIntegrationTest {

    private static final int MAX_PROVIDER_CALLS = 6;
    private static final long MAX_OUTPUT_TOKENS_PER_RESULT = 1024;

    @Autowired
    private AiProviderGateway providerGateway;

    @Autowired
    private PromptRegistry promptRegistry;

    @Autowired
    private PromptRenderer promptRenderer;

    @Autowired
    private IssueDetectionPromptFactory issueDetectionPromptFactory;

    @Autowired
    private SynthesisPromptFactory synthesisPromptFactory;

    @Autowired
    private DraftGenerationPromptFactory draftGenerationPromptFactory;

    @Autowired
    private DraftReviewPromptFactory draftReviewPromptFactory;

    @Autowired
    private AiProperties aiProperties;

    private final AtomicInteger providerCalls = new AtomicInteger();

    @Test
    @Timeout(300)
    void verifiesConnectivityMetadataAndFourFeatureContractsWithSyntheticData() {
        AiProviderResponse text = call(new AiProviderRequest(
                AiFeature.DRAFT_REVIEW,
                "This is a bounded connectivity test.",
                "Reply with only pong. The input is synthetic."
        ));
        assertMetadata(text.usageMetadata());

        var commonPrompt = promptRenderer.render(
                promptRegistry.get(new PromptTemplateId("contract-summary", 1)),
                Map.of("sourceText", "Synthetic: team opinions become one shared draft.")
        );
        StructuredAiProviderResponse<SmokeSummary> common = call(new StructuredAiProviderRequest<>(
                AiFeature.DRAFT_REVIEW,
                commonPrompt,
                StructuredOutputDefinition.of(
                        new OutputSchemaId("openai-smoke-summary", 1), SmokeSummary.class
                ),
                StructuredOutputValidationContext.empty()
        ));
        assertThat(common.result().summary()).isNotBlank();
        assertMetadata(common.usageMetadata());

        StructuredAiProviderResponse<IssueDetectionOutput> issues = call(
                issueDetectionPromptFactory.providerRequest(issueContext())
        );
        assertThat(issues.result().issues()).isNotNull();

        StructuredAiProviderResponse<SynthesisAiOutput> synthesis = call(
                synthesisPromptFactory.providerRequest(synthesisContext(), Set.of(1L, 2L))
        );
        assertThat(synthesis.result().coveredOpinionIds()).containsExactlyInAnyOrder(1L, 2L);

        StructuredAiProviderResponse<DraftGenerationOutput> draft = call(
                draftGenerationPromptFactory.providerRequest(draftContext())
        );
        assertThat(draft.result().content()).contains("[미확인:GAP-13]");

        StructuredAiProviderResponse<DraftReviewOutput> review = call(
                draftReviewPromptFactory.providerRequest(reviewContext())
        );
        assertThat(review.result().findings()).isNotNull();

        assertThat(providerCalls).hasValueLessThanOrEqualTo(MAX_PROVIDER_CALLS);
    }

    private AiProviderResponse call(AiProviderRequest request) {
        requireCallBudget();
        return providerGateway.generate(request);
    }

    private <T> StructuredAiProviderResponse<T> call(StructuredAiProviderRequest<T> request) {
        requireCallBudget();
        StructuredAiProviderResponse<T> response = providerGateway.generateStructured(request);
        assertMetadata(response.usageMetadata());
        return response;
    }

    private void requireCallBudget() {
        if (providerCalls.incrementAndGet() > MAX_PROVIDER_CALLS) {
            throw new IllegalStateException("OpenAI smoke request budget을 초과했습니다.");
        }
    }

    private void assertMetadata(AiUsageMetadata usage) {
        assertThat(usage.providerId()).isEqualTo("openai");
        assertThat(usage.providerRequestId()).isNotBlank();
        assertThat(usage.modelId()).startsWith(aiProperties.openai().model());
        assertThat(usage.inputTokens()).isNotNull().isNotNegative();
        assertThat(usage.outputTokens()).isNotNull().isPositive()
                .isLessThanOrEqualTo(MAX_OUTPUT_TOKENS_PER_RESULT);
    }

    private IssueDetectionContext issueContext() {
        return new IssueDetectionContext(
                project(), brief(), section(ProjectSectionStatus.SYNTHESIZING),
                List.of(
                        opinion(1L, "출시일은 8월 20일이어야 합니다."),
                        opinion(2L, "출시일은 9월 10일이어야 합니다.")
                )
        );
    }

    private SynthesisPromptContext synthesisContext() {
        return new SynthesisPromptContext(
                SynthesisPromptContext.PARTIAL,
                List.of(
                        opinion(1L, "모바일 우선 출시에는 동의합니다."),
                        opinion(2L, "모바일 우선이며 출시일 결정이 필요합니다.")
                ),
                List.of(),
                List.of()
        );
    }

    private DraftGenerationContext draftContext() {
        return new DraftGenerationContext(
                project(), brief(), section(ProjectSectionStatus.SYNTHESIZING),
                new AiDraftSynthesisContext(
                        10L,
                        2,
                        "모바일 우선 출시에 합의했습니다.",
                        List.of(),
                        List.of(new AiOpinionEvidenceContext(1L, "모바일 우선 출시에 동의합니다.")),
                        List.of(),
                        List.of(new AiGapIssueContext(
                                13L, "출시 일정 근거가 없습니다.", false, List.of()
                        )),
                        false
                ),
                new AiBaseDraftContext(0, null),
                List.of()
        );
    }

    private DraftReviewContext reviewContext() {
        return new DraftReviewContext(
                project(),
                section(ProjectSectionStatus.DRAFTING),
                30L,
                3,
                "모바일 우선으로 출시합니다. 일정은 추가 결정이 필요합니다.",
                List.of(),
                new AiInputSnapshotHasher().hashCanonical("synthetic-review-v3")
        );
    }

    private AiProjectIdentity project() {
        return new AiProjectIdentity(1L, "Synthetic Wevo", OutputType.PROPOSAL);
    }

    private AiProjectBrief brief() {
        return new AiProjectBrief("synthetic 설명", "팀 협업", "프로젝트 팀");
    }

    private AiSectionContext section(ProjectSectionStatus status) {
        return new AiSectionContext(
                2L, "문제 정의", 1, status, 2, false,
                new AiTemplateContext("problem", "문제를 설명합니다.", "근거와 미확정 항목을 구분합니다.")
        );
    }

    private AiOpinionContext opinion(Long id, String content) {
        return new AiOpinionContext(id, "member-" + id, content, "2026-07-31T10:00:00");
    }

    private record SmokeSummary(String summary) {
    }
}
