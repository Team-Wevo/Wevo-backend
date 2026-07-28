package com.wevo.backend.section.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.section.domain.DriftStatus;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SectionSynthesisStateServiceTest {

    @Mock private ProjectSectionRepository projectSectionRepository;
    @Mock private SectionDraftRepository sectionDraftRepository;
    @InjectMocks private SectionSynthesisStateService service;

    @Test
    void successfulResynthesisClearsOnlySynthesisStaleAndPreservesDrift() {
        Project project = Project.builder()
                .title("project")
                .resultType(OutputType.PRESENTATION)
                .status(ProjectStatus.ACTIVE)
                .build();
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .title("section")
                .sectionOrder(1)
                .status(ProjectSectionStatus.SYNTHESIZING)
                .build();
        section.markSynthesisStale();
        section.markDriftReviewRequired();
        given(projectSectionRepository.findByIdForUpdate(1L))
                .willReturn(Optional.of(section));

        service.clearSynthesisStale(1L);

        assertThat(section.isSynthesisStale()).isFalse();
        assertThat(section.getDriftStatus()).isEqualTo(DriftStatus.REVIEW_REQUIRED);
    }
}
