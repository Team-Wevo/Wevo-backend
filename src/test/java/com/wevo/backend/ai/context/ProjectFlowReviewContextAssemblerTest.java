package com.wevo.backend.ai.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.service.ProjectAiContext;
import com.wevo.backend.project.service.ProjectAiContextQueryService;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.service.ConfirmedSectionContent;
import com.wevo.backend.section.service.SectionConfirmationQueryService;
import com.wevo.backend.section.service.SectionConfirmationSummary;
import com.wevo.backend.section.service.SectionDependencyEdge;
import com.wevo.backend.section.service.SectionDependencyQueryService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProjectFlowReviewContextAssemblerTest {
    private ProjectAiContextQueryService projectQuery;
    private SectionConfirmationQueryService confirmationQuery;
    private SectionDependencyQueryService dependencyQuery;
    private VerifiedProjectAccess access;
    private ProjectFlowReviewContextAssembler assembler;

    @BeforeEach
    void setUp() {
        projectQuery = mock(ProjectAiContextQueryService.class);
        confirmationQuery = mock(SectionConfirmationQueryService.class);
        dependencyQuery = mock(SectionDependencyQueryService.class);
        access = mock(VerifiedProjectAccess.class);
        Project project = mock(Project.class);
        given(access.project()).willReturn(project);
        given(access.projectId()).willReturn(1L);
        given(project.getResultType()).willReturn(OutputType.PROPOSAL);
        given(projectQuery.getProjectContext(access)).willReturn(new ProjectAiContext(
                1L, "프로젝트", "설명", "아이디어", "고객", OutputType.PROPOSAL));
        given(confirmationQuery.getConfirmationSummary(access)).willReturn(new SectionConfirmationSummary(2, 2));
        assembler = new ProjectFlowReviewContextAssembler(projectQuery, confirmationQuery,
                dependencyQuery, properties(), new AiInputSnapshotHasher());
    }

    @Test
    void canonicalSnapshotIsIndependentOfSectionAndDependencyRepositoryOrder() {
        given(confirmationQuery.findConfirmedContents(access)).willReturn(List.of(second(), first(1)));
        given(dependencyQuery.findRequiresEdges(access)).willReturn(List.of(
                new SectionDependencyEdge("execution", "problem")));
        var baseline = assembler.assemble(access);

        given(confirmationQuery.findConfirmedContents(access)).willReturn(List.of(first(1), second()));
        var reordered = assembler.assemble(access);

        assertThat(reordered.snapshot().inputSnapshotHash())
                .isEqualTo(baseline.snapshot().inputSnapshotHash());
        assertThat(baseline.context().sections()).extracting(ProjectFlowReviewSectionContext::sectionId)
                .containsExactly(10L, 20L);
        assertThat(baseline.context().dependencies()).containsExactly(new ProjectFlowDependencyContext(20L, 10L));
    }

    @Test
    void confirmedVersionAndDependencyGraphChangeSnapshot() {
        given(confirmationQuery.findConfirmedContents(access)).willReturn(List.of(first(1), second()));
        given(dependencyQuery.findRequiresEdges(access)).willReturn(List.of(
                new SectionDependencyEdge("execution", "problem")));
        String baseline = assembler.assemble(access).snapshot().inputSnapshotHash();

        given(confirmationQuery.findConfirmedContents(access)).willReturn(List.of(first(2), second()));
        String versionChanged = assembler.assemble(access).snapshot().inputSnapshotHash();
        given(dependencyQuery.findRequiresEdges(access)).willReturn(List.of());
        String dependencyChanged = assembler.assemble(access).snapshot().inputSnapshotHash();

        assertThat(List.of(baseline, versionChanged, dependencyChanged)).doesNotHaveDuplicates();
    }

    @Test
    void templateLessConfirmedSectionIsRejectedAsInvalidDefaultSection() {
        given(confirmationQuery.findConfirmedContents(access)).willReturn(List.of(
                new ConfirmedSectionContent(10L, null, 1, 1,
                        "문제", null, null, "문제 본문"),
                second()));

        assertThatThrownBy(() -> assembler.assemble(access))
                .isInstanceOf(AiContextAssemblyException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("흐름 점검 필수 문자열이 누락되었습니다.");
    }

    private ConfirmedSectionContent first(int version) {
        return new ConfirmedSectionContent(10L, "problem", 1, version, "문제", "목적", "가이드", "문제 본문");
    }
    private ConfirmedSectionContent second() {
        return new ConfirmedSectionContent(20L, "execution", 2, 1, "실행", "목적", "가이드", "실행 본문");
    }
    private AiProperties properties() {
        return new AiProperties("none", new AiProperties.ModelOptions(
                "test-model", Duration.ofSeconds(1), 100_000, 4_096, 120_000, 8_000,
                AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1,
                AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1,
                0, Duration.ZERO, Duration.ZERO), Map.of(), null);
    }
}
