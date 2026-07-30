package com.wevo.backend.section;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.section.domain.AiCheckStatus;
import com.wevo.backend.section.domain.DraftLease;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.domain.SectionTemplate;
import com.wevo.backend.section.dto.request.SectionDraftSaveRequest;
import com.wevo.backend.section.repository.SectionDraftRepository;
import com.wevo.backend.section.repository.SectionTemplateRepository;
import com.wevo.backend.section.service.SectionDraftService;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
class SectionDriftRollbackIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired private EntityManager em;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private SectionDraftService sectionDraftService;
    @Autowired private SectionDraftRepository sectionDraftRepository;
    @Autowired private SectionTemplateRepository sectionTemplateRepository;

    @Test
    void dependentPropagationFailureRollsBackSourceDraftAndOverlays() {
        Fixture fixture = transactionTemplate.execute(status -> persistFixture());
        try {
            assertThatThrownBy(() -> sectionDraftService.saveDraft(
                    fixture.sectionId(),
                    fixture.userId(),
                    new SectionDraftSaveRequest("롤백되어야 하는 v2", 1)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("누락");

            transactionTemplate.executeWithoutResult(status -> {
                ProjectSection section = em.find(ProjectSection.class, fixture.sectionId());
                assertThat(section.getStatus()).isEqualTo(ProjectSectionStatus.CONFIRMED);
                assertThat(section.getAiCheckStatus()).isEqualTo(AiCheckStatus.CURRENT);
                assertThat(sectionDraftRepository
                        .findTopByProjectSection_IdOrderByVersionDesc(fixture.sectionId())
                        .orElseThrow()
                        .getVersion()).isEqualTo(1);
            });
        } finally {
            cleanup(fixture);
        }
    }

    /**
     * target-user의 직접 하위(solution-direction) project section을 의도적으로 만들지 않는다.
     * 기준 dependency는 존재하므로 전파 대상 해석 단계에서 실패한다.
     */
    private Fixture persistFixture() {
        User owner = User.builder()
                .name("rollback-owner")
                .email("rollback-ai11@wevo.com")
                .status(UserStatus.ACTIVE)
                .build();
        em.persist(owner);
        Project project = Project.builder()
                .owner(owner)
                .title("rollback project")
                .resultType(OutputType.PRESENTATION)
                .audience("team")
                .status(ProjectStatus.ACTIVE)
                .build();
        em.persist(project);
        em.persist(ProjectMember.builder()
                .project(project)
                .user(owner)
                .role(ProjectMemberRole.OWNER)
                .joinedAt(LocalDateTime.now(KST))
                .build());
        SectionTemplate targetTemplate = sectionTemplateRepository
                .findByResultTypeOrderByOrderNo(OutputType.PRESENTATION)
                .stream()
                .filter(template -> template.getSectionKey().equals("target-user"))
                .findFirst()
                .orElseThrow();
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .template(targetTemplate)
                .title(targetTemplate.getTitle())
                .sectionOrder(targetTemplate.getOrderNo())
                .status(ProjectSectionStatus.CONFIRMED)
                .build();
        section.recordConfirmedVersion(1);
        section.bindCurrentAiCheck();
        em.persist(section);
        em.persist(SectionDraft.builder()
                .projectSection(section)
                .content("확정 v1")
                .version(1)
                .lastEditor(owner)
                .build());
        em.persist(DraftLease.builder()
                .projectSection(section)
                .holderUserId(owner.getId())
                .leaseUntil(LocalDateTime.now(KST).plusHours(1))
                .build());
        em.flush();
        return new Fixture(owner.getId(), project.getId(), section.getId());
    }

    private void cleanup(Fixture fixture) {
        transactionTemplate.executeWithoutResult(status -> {
            em.createNativeQuery("DELETE FROM draft_leases WHERE project_section_id = :sectionId")
                    .setParameter("sectionId", fixture.sectionId())
                    .executeUpdate();
            em.createNativeQuery("DELETE FROM section_drafts WHERE project_section_id = :sectionId")
                    .setParameter("sectionId", fixture.sectionId())
                    .executeUpdate();
            em.createNativeQuery("DELETE FROM project_sections WHERE id = :sectionId")
                    .setParameter("sectionId", fixture.sectionId())
                    .executeUpdate();
            em.createNativeQuery("DELETE FROM project_members WHERE project_id = :projectId")
                    .setParameter("projectId", fixture.projectId())
                    .executeUpdate();
            em.createNativeQuery("DELETE FROM projects WHERE id = :projectId")
                    .setParameter("projectId", fixture.projectId())
                    .executeUpdate();
            em.createNativeQuery("DELETE FROM users WHERE id = :userId")
                    .setParameter("userId", fixture.userId())
                    .executeUpdate();
        });
    }

    private record Fixture(Long userId, Long projectId, Long sectionId) {
    }
}
