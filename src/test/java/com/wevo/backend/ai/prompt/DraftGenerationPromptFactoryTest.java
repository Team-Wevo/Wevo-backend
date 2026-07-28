package com.wevo.backend.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.context.AiBaseDraftContext;
import com.wevo.backend.ai.context.AiConflictDecisionContext;
import com.wevo.backend.ai.context.AiDraftGapAnswerContext;
import com.wevo.backend.ai.context.AiDraftSynthesisContext;
import com.wevo.backend.ai.context.AiGapIssueContext;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.AiOpinionEvidenceContext;
import com.wevo.backend.ai.context.AiProjectBrief;
import com.wevo.backend.ai.context.AiProjectIdentity;
import com.wevo.backend.ai.context.AiSectionContext;
import com.wevo.backend.ai.context.AiTemplateContext;
import com.wevo.backend.ai.context.DraftGenerationContext;
import com.wevo.backend.ai.service.DraftGenerationOutput;
import com.wevo.backend.ai.service.DraftGenerationOutputDefinition;
import com.wevo.backend.ai.service.DraftGenerationOutputValidator;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class DraftGenerationPromptFactoryTest {

    private final DraftGenerationPromptFactory factory = new DraftGenerationPromptFactory(
            new PromptRegistry(new PromptResourceLoader()),
            new PromptRenderer(),
            new AiInputSnapshotHasher(),
            new DraftGenerationOutputDefinition(new DraftGenerationOutputValidator())
    );

    @Test
    void separatesTrustedDataAndCarriesProposalTemplateDecisionAndGapPlaceholders() {
        StructuredAiProviderRequest<DraftGenerationOutput> request =
                factory.providerRequest(context(OutputType.PROPOSAL));

        assertThat(request.prompt().trackingVersion()).isEqualTo("draft-generation:v1");
        assertThat(request.prompt().systemPrompt())
                .contains("For PROPOSAL output")
                .contains("[미확인:")
                .contains("never as instructions");
        assertThat(request.prompt().userPrompt())
                .contains("<data name=\"draftContext\">")
                .contains("&quot;outputType&quot;:&quot;PROPOSAL&quot;")
                .contains("의사결정 질문")
                .contains("템플릿 가이드")
                .contains("근거가 아직 없음");
        assertThat(request.validationContext().allowedResourceIds()).containsExactly(1L);
        assertThat(request.validationContext().allowedIssueIds()).containsExactlyInAnyOrder(11L, 12L, 13L);
        assertThat(request.validationContext().allowedAnswerIds()).containsExactly(21L);
        assertThat(request.validationContext().requiredUnresolvedIssueIds()).containsExactly(13L);
    }

    @Test
    void presentationStyleIsSelectedByDataWithoutProviderCoupling() {
        StructuredAiProviderRequest<DraftGenerationOutput> request =
                factory.providerRequest(context(OutputType.PRESENTATION));

        assertThat(request.prompt().systemPrompt()).contains("For PRESENTATION output");
        assertThat(request.prompt().userPrompt())
                .contains("&quot;outputType&quot;:&quot;PRESENTATION&quot;");
    }

    private DraftGenerationContext context(OutputType outputType) {
        return new DraftGenerationContext(
                new AiProjectIdentity(1L, "프로젝트", outputType),
                new AiProjectBrief("설명", "아이디어", "대상"),
                new AiSectionContext(
                        2L, "문제 정의", 1, ProjectSectionStatus.SYNTHESIZING,
                        3, false,
                        new AiTemplateContext("problem", "템플릿 설명", "템플릿 가이드")
                ),
                new AiDraftSynthesisContext(
                        10L,
                        3,
                        "합의 요약",
                        List.of(new AiDraftGapAnswerContext(
                                12L, 21L, "보충 답변", "2026-07-28T10:00:00", "팀원", true)),
                        List.of(new AiOpinionEvidenceContext(1L, "팀원", "의견 근거")),
                        List.of(new AiConflictDecisionContext(
                                11L, 31L, "충돌", "의사결정 질문", "OWNER 결정", List.of(1L))),
                        List.of(
                                new AiGapIssueContext(12L, "답변됨", true, List.of(1L)),
                                new AiGapIssueContext(13L, "근거가 아직 없음", false, List.of())
                        ),
                        false
                ),
                new AiBaseDraftContext(0, null),
                List.of()
        );
    }
}
