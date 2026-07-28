package com.wevo.backend.ai.prompt;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.AiOpinionContext;
import com.wevo.backend.ai.context.AiProjectBrief;
import com.wevo.backend.ai.context.AiProjectIdentity;
import com.wevo.backend.ai.context.AiSectionContext;
import com.wevo.backend.ai.context.AiTemplateContext;
import com.wevo.backend.ai.context.IssueDetectionContext;
import com.wevo.backend.ai.dto.model.IssueDetectionOutput;
import com.wevo.backend.ai.service.IssueDetectionOutputDefinition;
import com.wevo.backend.ai.service.IssueDetectionOutputValidator;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssueDetectionPromptFactoryTest {

    private final IssueDetectionPromptFactory factory = new IssueDetectionPromptFactory(
            new PromptRegistry(new PromptResourceLoader()),
            new PromptRenderer(),
            new AiInputSnapshotHasher(),
            new IssueDetectionOutputDefinition(new IssueDetectionOutputValidator())
    );

    @Test
    void rendersVersionedXmlDelimitedContextAndIndependentSchema() {
        StructuredAiProviderRequest<IssueDetectionOutput> request = factory.providerRequest(context());

        assertThat(request.prompt().trackingVersion()).isEqualTo("issue-detection:v1");
        assertThat(request.outputDefinition().schemaId().trackingValue())
                .isEqualTo("issue-detection-output:v1");
        assertThat(request.prompt().userPrompt())
                .contains("<data name=\"issueDetectionContext\">")
                .contains("&quot;opinionId&quot;:1")
                .contains("목표 &amp; 일정")
                .doesNotContain("authorReference")
                .doesNotContain("email");
        assertThat(request.validationContext().allowedResourceIds()).containsExactly(1L);
        assertThat(factory.tokenBudgetInput(context()).segments())
                .hasSize(2)
                .allMatch(segment -> !segment.isBlank());
    }

    @Test
    void rejectsDuplicateOpinionIdsBeforeProviderRequest() {
        IssueDetectionContext context = context();
        IssueDetectionContext duplicated = new IssueDetectionContext(
                context.project(),
                context.projectBrief(),
                context.section(),
                List.of(context.opinions().getFirst(), context.opinions().getFirst())
        );

        assertThatThrownBy(() -> factory.providerRequest(duplicated))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private IssueDetectionContext context() {
        return new IssueDetectionContext(
                new AiProjectIdentity(10L, "프로젝트", OutputType.PROPOSAL),
                new AiProjectBrief("설명", "아이디어", "사용자"),
                new AiSectionContext(
                        20L,
                        "문제",
                        1,
                        ProjectSectionStatus.SYNTHESIZING,
                        2,
                        false,
                        new AiTemplateContext("problem", "설명", "가이드")
                ),
                List.of(new AiOpinionContext(
                        1L,
                        "member-1",
                        "목표 & 일정",
                        "2026-07-25T10:00:00"
                ))
        );
    }
}
