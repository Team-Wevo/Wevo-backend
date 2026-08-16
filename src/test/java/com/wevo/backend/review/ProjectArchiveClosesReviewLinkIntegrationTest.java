package com.wevo.backend.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.service.ProjectService;
import com.wevo.backend.review.domain.ReviewLink;
import com.wevo.backend.review.domain.ReviewLinkStatus;
import com.wevo.backend.review.repository.ReviewLinkRepository;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 보관(삭제) 시 활성 외부 검토 링크가 실제로 닫히는지 검증한다. (#230)
 *
 * <p>{@code ProjectService.archiveProject} 를 실제로 호출해 {@code ProjectArchivedEvent} 배선까지
 * 함께 본다 — 리스너({@code ReviewLinkArchiveCloser})가 같은 트랜잭션에서 활성 링크를 CLOSED 로
 * 전이하는지는 실제 DB 로만 확인된다. (CLAUDE.md §8)
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
@Transactional
class ProjectArchiveClosesReviewLinkIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private ProjectService projectService;
    @Autowired
    private ReviewLinkRepository reviewLinkRepository;
    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("보관하면 그 프로젝트의 활성 외부 검토 링크가 CLOSED 로 닫힌다")
    void archive_closesActiveReviewLinks() {
        User owner = persistUser("owner@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project, 1);
        ReviewLink active = persistReviewLink(section, owner, "hash-active", ReviewLinkStatus.ACTIVE);
        em.flush();

        projectService.archiveProject(owner.getId(), project.getId());
        em.flush();
        em.clear();

        assertThat(reviewLinkRepository.findById(active.getId()).orElseThrow().getStatus())
                .as("보관되면 활성 외부 검토 링크는 닫혀 외부 제출·조회가 거부된다")
                .isEqualTo(ReviewLinkStatus.CLOSED);
    }

    @Test
    @DisplayName("다른 프로젝트의 활성 링크와 이미 종료된 링크는 보관 정리에 영향받지 않는다")
    void archive_leavesOtherProjectAndAlreadyClosedLinks() {
        User owner = persistUser("owner2@wevo.com");
        Project archived = persistProject(owner);
        persistMember(archived, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(archived, 1);
        ReviewLink alreadyClosed =
                persistReviewLink(section, owner, "hash-closed", ReviewLinkStatus.CLOSED);

        Project other = persistProject(owner);
        persistMember(other, owner, ProjectMemberRole.OWNER);
        ProjectSection otherSection = persistSection(other, 1);
        ReviewLink otherActive =
                persistReviewLink(otherSection, owner, "hash-other", ReviewLinkStatus.ACTIVE);
        em.flush();

        projectService.archiveProject(owner.getId(), archived.getId());
        em.flush();
        em.clear();

        assertThat(reviewLinkRepository.findById(alreadyClosed.getId()).orElseThrow().getStatus())
                .isEqualTo(ReviewLinkStatus.CLOSED);
        assertThat(reviewLinkRepository.findById(otherActive.getId()).orElseThrow().getStatus())
                .as("다른 프로젝트의 링크는 그대로 활성이다")
                .isEqualTo(ReviewLinkStatus.ACTIVE);
    }

    private ReviewLink persistReviewLink(
            ProjectSection section, User createdBy, String tokenHash, ReviewLinkStatus status) {
        ReviewLink link = ReviewLink.builder()
                .projectSection(section)
                .createdBy(createdBy)
                .tokenHash(tokenHash)
                .contentSnapshot("검토 대상 본문")
                .contentVersion(1)
                .status(status)
                .build();
        em.persist(link);
        return link;
    }

    private User persistUser(String email) {
        User user = User.builder()
                .name(email.substring(0, email.indexOf('@')))
                .email(email)
                .status(UserStatus.ACTIVE)
                .build();
        em.persist(user);
        return user;
    }

    private Project persistProject(User owner) {
        Project project = Project.builder()
                .owner(owner)
                .title("위보 기획")
                .resultType(OutputType.PRESENTATION)
                .audience("프로젝트 팀원")
                .status(ProjectStatus.ACTIVE)
                .build();
        em.persist(project);
        return project;
    }

    private ProjectSection persistSection(Project project, int order) {
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .title("섹션 " + order)
                .sectionOrder(order)
                .status(ProjectSectionStatus.REVIEWING)
                .build();
        em.persist(section);
        return section;
    }

    private void persistMember(Project project, User user, ProjectMemberRole role) {
        em.persist(ProjectMember.builder()
                .project(project)
                .user(user)
                .role(role)
                .joinedAt(LocalDateTime.now(KST))
                .build());
    }
}
