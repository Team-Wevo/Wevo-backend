package com.wevo.backend.section.service;

import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.review.service.TeamReviewService;
import com.wevo.backend.section.domain.AiCheckStatus;
import com.wevo.backend.section.domain.DriftStatus;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionTemplate;
import com.wevo.backend.section.domain.TemplateDependency;
import com.wevo.backend.section.domain.TemplateDependencyType;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.section.repository.TemplateDependencyRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SectionDriftServiceTest {

    private static final long PROJECT_ID = 1L;
    private static final long ACTOR_ID = 2L;

    @Mock private ProjectAccessGuard projectAccessGuard;
    @Mock private ProjectSectionRepository projectSectionRepository;
    @Mock private TemplateDependencyRepository templateDependencyRepository;
    @Mock private SectionStatusService sectionStatusService;
    @Mock private TeamReviewService teamReviewService;

    private SectionDriftService service;
    private Project project;
    private ProjectMember actor;

    @BeforeEach
    void setUp() {
        service = new SectionDriftService(
                projectAccessGuard,
                projectSectionRepository,
                templateDependencyRepository,
                sectionStatusService,
                teamReviewService
        );
        User user = User.builder().name("actor").status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", ACTOR_ID);
        project = Project.builder()
                .owner(user)
                .title("project")
                .resultType(OutputType.PRESENTATION)
                .audience("team")
                .status(ProjectStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);
        actor = ProjectMember.builder()
                .project(project)
                .user(user)
                .role(ProjectMemberRole.MEMBER)
                .build();
        given(projectAccessGuard.requireParticipant(PROJECT_ID, ACTOR_ID)).willReturn(actor);
        doAnswer(invocation -> {
            ProjectSection section = invocation.getArgument(0);
            section.changeStatus(ProjectSectionStatus.REVIEWING, LocalDateTime.now());
            return null;
        }).when(sectionStatusService).markReviewingAfterConfirmedContentChange(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(actor),
                org.mockito.ArgumentMatchers.anyInt());
        doAnswer(invocation -> {
            ProjectSection section = invocation.getArgument(0);
            section.changeStatus(ProjectSectionStatus.REVIEWING, LocalDateTime.now());
            return null;
        }).when(sectionStatusService).markReviewingAfterPrerequisiteChange(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(actor),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void propagatesOnlyDirectDependentsByStatusWithoutChangingAiOverlays() {
        SectionTemplate sourceTemplate = template(10L, "source", 1);
        SectionTemplate confirmedTemplate = template(11L, "confirmed-child", 2);
        SectionTemplate reviewingTemplate = template(12L, "reviewing-child", 3);
        SectionTemplate draftingTemplate = template(13L, "drafting-child", 4);
        ProjectSection source = section(100L, sourceTemplate, 1, ProjectSectionStatus.CONFIRMED);
        ProjectSection confirmed = section(101L, confirmedTemplate, 2, ProjectSectionStatus.CONFIRMED);
        ProjectSection reviewing = section(102L, reviewingTemplate, 3, ProjectSectionStatus.REVIEWING);
        ProjectSection drafting = section(103L, draftingTemplate, 4, ProjectSectionStatus.DRAFTING);
        source.recordConfirmedVersion(3);
        confirmed.recordConfirmedVersion(5);
        confirmed.bindCurrentAiCheck();
        reviewing.bindCurrentAiCheck();
        drafting.bindCurrentAiCheck();
        drafting.markSynthesisStale();

        List<TemplateDependency> dependencies = List.of(
                dependency(confirmedTemplate, sourceTemplate),
                dependency(reviewingTemplate, sourceTemplate),
                dependency(draftingTemplate, sourceTemplate)
        );
        given(templateDependencyRepository.findDirectDependents(
                10L, OutputType.PRESENTATION, TemplateDependencyType.REQUIRES))
                .willReturn(dependencies);
        given(projectSectionRepository.findDependentsForUpdate(
                PROJECT_ID, List.of(11L, 12L, 13L)))
                .willReturn(List.of(confirmed, reviewing, drafting));

        List<DriftedSection> result =
                service.propagateConfirmedContentChange(source, ACTOR_ID, 4);

        assertThat(source.getStatus()).isEqualTo(ProjectSectionStatus.REVIEWING);
        assertThat(result).extracting(DriftedSection::sectionId)
                .containsExactly(101L, 102L, 103L);
        assertThat(confirmed.getStatus()).isEqualTo(ProjectSectionStatus.REVIEWING);
        assertThat(reviewing.getStatus()).isEqualTo(ProjectSectionStatus.REVIEWING);
        assertThat(drafting.getStatus()).isEqualTo(ProjectSectionStatus.DRAFTING);
        assertThat(List.of(confirmed, reviewing, drafting))
                .allMatch(section -> section.getDriftStatus() == DriftStatus.REVIEW_REQUIRED);
        assertThat(List.of(confirmed, reviewing, drafting))
                .allMatch(section -> section.getAiCheckStatus() == AiCheckStatus.CURRENT);
        assertThat(drafting.isSynthesisStale()).isTrue();

        verify(sectionStatusService).markReviewingAfterConfirmedContentChange(source, actor, 4);
        verify(sectionStatusService).markReviewingAfterPrerequisiteChange(confirmed, actor, 5);
        verify(teamReviewService).markSectionTeamReviewsOutdated(101L);
        verify(teamReviewService).markSectionTeamReviewsOutdated(102L);
        verify(teamReviewService, never()).markSectionTeamReviewsOutdated(103L);
        verify(templateDependencyRepository, times(1)).findDirectDependents(
                10L, OutputType.PRESENTATION, TemplateDependencyType.REQUIRES);
    }

    @Test
    void unconfirmedContentChangeIsNoOpWithoutDependencyLookup() {
        ProjectSection source = section(
                100L, template(10L, "source", 1), 1, ProjectSectionStatus.DRAFTING);

        assertThat(service.propagateConfirmedContentChange(source, ACTOR_ID, 1)).isEmpty();

        verify(projectAccessGuard, never()).requireParticipant(PROJECT_ID, ACTOR_ID);
        verify(templateDependencyRepository, never()).findDirectDependents(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingDependentSectionFailsInsteadOfPartiallyAcceptingGraph() {
        SectionTemplate sourceTemplate = template(10L, "source", 1);
        SectionTemplate dependentTemplate = template(11L, "dependent", 2);
        ProjectSection source = section(100L, sourceTemplate, 1, ProjectSectionStatus.CONFIRMED);
        source.recordConfirmedVersion(1);
        given(templateDependencyRepository.findDirectDependents(
                10L, OutputType.PRESENTATION, TemplateDependencyType.REQUIRES))
                .willReturn(List.of(dependency(dependentTemplate, sourceTemplate)));
        given(projectSectionRepository.findDependentsForUpdate(PROJECT_ID, List.of(11L)))
                .willReturn(List.of());

        assertThatThrownBy(() ->
                service.propagateConfirmedContentChange(source, ACTOR_ID, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("누락");
    }

    private SectionTemplate template(Long id, String key, int order) {
        SectionTemplate template = SectionTemplate.builder()
                .resultType(OutputType.PRESENTATION)
                .sectionKey(key)
                .title(key)
                .orderNo(order)
                .isRequired(true)
                .build();
        ReflectionTestUtils.setField(template, "id", id);
        return template;
    }

    private ProjectSection section(
            Long id,
            SectionTemplate template,
            int order,
            ProjectSectionStatus status
    ) {
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .template(template)
                .title(template.getTitle())
                .sectionOrder(order)
                .status(status)
                .build();
        ReflectionTestUtils.setField(section, "id", id);
        return section;
    }

    private TemplateDependency dependency(SectionTemplate from, SectionTemplate to) {
        return TemplateDependency.builder()
                .fromTemplate(from)
                .toTemplate(to)
                .dependencyType(TemplateDependencyType.REQUIRES)
                .build();
    }
}
