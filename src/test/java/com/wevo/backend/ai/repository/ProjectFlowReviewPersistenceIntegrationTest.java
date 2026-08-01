package com.wevo.backend.ai.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.AiProjectBrief;
import com.wevo.backend.ai.context.AiProjectIdentity;
import com.wevo.backend.ai.context.AiTemplateContext;
import com.wevo.backend.ai.context.ProjectFlowReviewContext;
import com.wevo.backend.ai.context.ProjectFlowReviewSectionContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.ProjectFlowFindingType;
import com.wevo.backend.ai.dto.model.ProjectFlowFindingOutput;
import com.wevo.backend.ai.dto.model.ProjectFlowReviewOutput;
import com.wevo.backend.ai.dto.model.ProjectFlowSectionExcerptOutput;
import com.wevo.backend.ai.service.ProjectFlowReviewPersistCommand;
import com.wevo.backend.ai.service.ProjectFlowReviewResultWriter;
import com.wevo.backend.global.config.JpaAuditingConfig;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.repository.ProjectRepository;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, PostgresTestContainerConfig.class,
        AiInputSnapshotHasher.class, ProjectFlowReviewResultWriter.class})
class ProjectFlowReviewPersistenceIntegrationTest {
    @Autowired UserRepository userRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectSectionRepository sectionRepository;
    @Autowired AiJobRepository jobRepository;
    @Autowired ProjectFlowCheckRepository checkRepository;
    @Autowired ProjectFlowCheckInputRepository inputRepository;
    @Autowired ProjectFlowCheckFindingRepository findingRepository;
    @Autowired ProjectFlowFindingSectionRepository referenceRepository;
    @Autowired ProjectFlowReviewResultWriter writer;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    private Project project;
    private ProjectSection first;
    private ProjectSection second;
    private AiJob job;

    @BeforeEach
    void setUp() {
        User owner = userRepository.save(User.builder().name("owner").email("flow@wevo.com")
                .status(UserStatus.ACTIVE).build());
        project = projectRepository.save(Project.builder().owner(owner).title("흐름 점검")
                .resultType(OutputType.PROPOSAL).audience("고객").status(ProjectStatus.ACTIVE).build());
        first = sectionRepository.save(ProjectSection.builder().project(project).title("문제")
                .sectionOrder(1).status(ProjectSectionStatus.CONFIRMED).confirmedVersion(1).build());
        second = sectionRepository.save(ProjectSection.builder().project(project).title("예산")
                .sectionOrder(2).status(ProjectSectionStatus.CONFIRMED).confirmedVersion(2).build());
        job = jobRepository.saveAndFlush(AiJob.queue(UUID.randomUUID(), project, null, owner,
                AiFeature.PROJECT_FLOW_REVIEW, "a".repeat(64), "project-flow-review-source:v1",
                "project-flow-review:v1", "project-flow-review-output:v1", "test-model", 4096,
                "b".repeat(64), LocalDateTime.of(2026, 8, 1, 10, 0)));
    }

    @Test
    void persistsCheckedVersionsAndFindingSectionForeignKeys() {
        Long id = writer.persist(new ProjectFlowReviewPersistCommand(job, context(), output()));
        entityManager.flush(); entityManager.clear();
        assertThat(checkRepository.findById(id)).isPresent().get()
                .satisfies(check -> assertThat(check.getFindingCount()).isEqualTo(1));
        assertThat(inputRepository.findAllByFlowCheck_IdOrderBySortOrder(id))
                .extracting(input -> input.getConfirmedVersion()).containsExactly(1, 2);
        assertThat(findingRepository.findAllByFlowCheck_IdOrderBySortOrder(id)).hasSize(1);
        assertThat(referenceRepository.findAllForCheck(id))
                .extracting(ref -> ref.getProjectSectionId()).containsExactly(first.getId(), second.getId());

        Long findingId = findingRepository.findAllByFlowCheck_IdOrderBySortOrder(id).getFirst().getId();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO project_flow_finding_sections
                    (finding_id, project_section_id, confirmed_version, target_excerpt, sort_order)
                VALUES (?, ?, 1, '원문', 3)
                """, findingId, Long.MAX_VALUE)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void migrationValidatesFeatureChecksAndIndexesSectionReferences() {
        assertThat(jdbc.queryForList("""
                SELECT conname
                FROM pg_constraint
                WHERE conname IN ('chk_ai_jobs_feature', 'chk_ai_usage_logs_feature')
                  AND convalidated
                ORDER BY conname
                """, String.class)).containsExactly(
                "chk_ai_jobs_feature", "chk_ai_usage_logs_feature");
        assertThat(jdbc.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname IN ('idx_project_flow_inputs_section',
                                    'idx_project_flow_finding_sections_section')
                ORDER BY indexname
                """, String.class)).containsExactly(
                "idx_project_flow_finding_sections_section", "idx_project_flow_inputs_section");
    }

    private ProjectFlowReviewContext context() {
        return new ProjectFlowReviewContext(new AiProjectIdentity(project.getId(), "흐름 점검", OutputType.PROPOSAL),
                new AiProjectBrief(null, null, "고객"), 2, 2,
                List.of(section(first, "problem", 1, 1, "예산은 100만원이다."),
                        section(second, "budget", 2, 2, "예산은 200만원이다.")), List.of());
    }
    private ProjectFlowReviewSectionContext section(ProjectSection section, String key, int order,
                                                     int version, String content) {
        return new ProjectFlowReviewSectionContext(section.getId(), key, order, version,
                section.getTitle(), new AiTemplateContext(key, null, null), content);
    }
    private ProjectFlowReviewOutput output() {
        return new ProjectFlowReviewOutput(List.of(new ProjectFlowFindingOutput(
                ProjectFlowFindingType.CLAIM_OR_NUMBER_CONTRADICTION,
                List.of(new ProjectFlowSectionExcerptOutput(first.getId(), "100만원"),
                        new ProjectFlowSectionExcerptOutput(second.getId(), "200만원")),
                "예산이 다릅니다.", "예산을 통일하세요.")));
    }
}
