package com.wevo.backend.opinion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionCollectionState;
import com.wevo.backend.opinion.domain.OpinionStatus;
import com.wevo.backend.opinion.dto.response.OpinionCollectionStatusResponse;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 의견 수집 현황의 <b>역할별 응답 범위</b>를 실제 PostgreSQL 로 검증한다. (#238)
 *
 * <p>단위 테스트는 {@code SectionAccessGuard} 를 모킹하므로 "요청자가 OWNER 인가"를 <b>테스트가
 * 정해준다</b> — 가드가 역할을 잘못 판정해도 통과한다. 실제로 막으려는 사고(팀원에게 제출자 명단이
 * 새는 것)는 멤버십 행의 role 을 읽는 경로 전체가 맞아야 막히므로, 진짜 멤버십으로 확인한다.
 *
 * <p>검증 대상은 <b>같은 데이터에 대해 역할만 바꿨을 때의 차이</b>다 — 집계는 같고 명단만 갈린다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
@Transactional
class CollectionStatusRoleScopeIntegrationTest {

    private static final LocalDateTime SUBMITTED_AT = LocalDateTime.of(2026, 7, 14, 12, 0);

    @Autowired
    private OpinionService opinionService;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("같은 섹션에서 팀장은 명단을 받고 팀원은 못 받는다 — 집계는 완전히 동일하다")
    void ownerSeesItemsAndMemberDoesNot() {
        User owner = persistUser("팀장");
        User submitter = persistUser("제출한팀원");
        User drafter = persistUser("작성중팀원");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, submitter, ProjectMemberRole.MEMBER);
        persistMember(project, drafter, ProjectMemberRole.MEMBER);
        ProjectSection section = persistSection(project, ProjectSectionStatus.COLLECTING);
        persistSubmittedOpinion(section, submitter);
        persistDraftOpinion(section, drafter);

        OpinionCollectionStatusResponse forOwner =
                opinionService.getCollectionStatus(section.getId(), owner.getId());
        OpinionCollectionStatusResponse forMember =
                opinionService.getCollectionStatus(section.getId(), drafter.getId());

        // 팀장 — 누가 어떤 상태인지 전부 본다.
        assertThat(forOwner.items())
                .extracting(item -> item.userId(), item -> item.state())
                .containsExactlyInAnyOrder(
                        org.assertj.core.api.Assertions.tuple(
                                owner.getId(), OpinionCollectionState.NOT_STARTED),
                        org.assertj.core.api.Assertions.tuple(
                                submitter.getId(), OpinionCollectionState.SUBMITTED),
                        org.assertj.core.api.Assertions.tuple(
                                drafter.getId(), OpinionCollectionState.DRAFTING));

        // 팀원 — 명단은 없다.
        assertThat(forMember.items()).isNull();

        // 집계는 역할과 무관하게 같아야 한다. 명단을 감추느라 수치까지 달라지면
        // 팀원 화면의 진행률이 팀장 화면과 어긋난다.
        assertThat(forMember.totalMembers()).isEqualTo(forOwner.totalMembers()).isEqualTo(3);
        assertThat(forMember.submittedCount()).isEqualTo(forOwner.submittedCount()).isEqualTo(1);
        assertThat(forMember.draftingCount()).isEqualTo(forOwner.draftingCount()).isEqualTo(1);
        assertThat(forMember.notStartedCount()).isEqualTo(forOwner.notStartedCount()).isEqualTo(1);
        assertThat(forMember.pendingCount()).isEqualTo(forOwner.pendingCount()).isEqualTo(2);
        assertThat(forMember.collectionOpen()).isEqualTo(forOwner.collectionOpen()).isTrue();
    }

    @Test
    @DisplayName("이미 제출한 팀원에게도 명단을 주지 않는다 — 기준은 제출 여부가 아니라 역할이다")
    void submittedMemberStillGetsNoItems() {
        // 제출자는 §4.3 공개 게이트가 열려 남의 의견 '본문'을 볼 수 있다(§3.4.4).
        // 그렇다고 수집 현황의 명단까지 열리는 것은 아니다 — 재편집 여부는 본문 조회로도 알 수 없다.
        User owner = persistUser("팀장");
        User submitter = persistUser("제출한팀원");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, submitter, ProjectMemberRole.MEMBER);
        ProjectSection section = persistSection(project, ProjectSectionStatus.COLLECTING);
        persistSubmittedOpinion(section, submitter);

        OpinionCollectionStatusResponse response =
                opinionService.getCollectionStatus(section.getId(), submitter.getId());

        assertThat(response.items()).isNull();
        assertThat(response.submittedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("수집 마감 후에도 역할 구분은 그대로다 — 팀원은 여전히 집계만 본다")
    void roleScopeSurvivesGateClose() {
        User owner = persistUser("팀장");
        User member = persistUser("팀원");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, member, ProjectMemberRole.MEMBER);
        ProjectSection section = persistSection(project, ProjectSectionStatus.SYNTHESIZING);
        persistSubmittedOpinion(section, member);

        OpinionCollectionStatusResponse forMember =
                opinionService.getCollectionStatus(section.getId(), member.getId());
        OpinionCollectionStatusResponse forOwner =
                opinionService.getCollectionStatus(section.getId(), owner.getId());

        assertThat(forMember.collectionOpen()).isFalse();
        assertThat(forMember.items()).isNull();
        assertThat(forOwner.items()).hasSize(2);
    }

    @Test
    @DisplayName("팀장 혼자인 프로젝트에서도 팀장은 명단을 받는다")
    void soloOwnerStillGetsItems() {
        User owner = persistUser("팀장");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project, ProjectSectionStatus.COLLECTING);

        OpinionCollectionStatusResponse response =
                opinionService.getCollectionStatus(section.getId(), owner.getId());

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).state()).isEqualTo(OpinionCollectionState.NOT_STARTED);
        assertThat(response.notStartedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 프로젝트의 팀장은 이 섹션의 명단은커녕 존재도 알 수 없다")
    void ownerOfAnotherProjectIsHiddenAsNotFound() {
        // 역할 판정이 "이 프로젝트의 멤버십"이 아니라 전역 역할로 새면, 남의 프로젝트 팀장에게
        // 명단이 열린다. 존재 숨김(404)이 그보다 먼저 걸려야 한다.
        User owner = persistUser("팀장");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project, ProjectSectionStatus.COLLECTING);

        User strangerOwner = persistUser("남의프로젝트팀장");
        Project otherProject = persistProject(strangerOwner);
        persistMember(otherProject, strangerOwner, ProjectMemberRole.OWNER);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.getCollectionStatus(section.getId(), strangerOwner.getId()));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_NOT_FOUND);
    }

    private User persistUser(String name) {
        User user = User.builder()
                .name(name)
                .email(name + "-" + System.nanoTime() + "@wevo.com")
                .status(UserStatus.ACTIVE)
                .build();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private Project persistProject(User owner) {
        Project project = Project.builder()
                .owner(owner)
                .title("수집 현황 권한 검증")
                .ideaText("팀 의견을 모아 하나의 결과물로 만든다.")
                .resultType(OutputType.PROPOSAL)
                .audience("교내 심사위원")
                .status(ProjectStatus.ACTIVE)
                .build();
        entityManager.persist(project);
        entityManager.flush();
        return project;
    }

    private void persistMember(Project project, User user, ProjectMemberRole role) {
        entityManager.persist(ProjectMember.builder()
                .project(project)
                .user(user)
                .role(role)
                .joinedAt(LocalDateTime.now())
                .build());
        entityManager.flush();
    }

    private ProjectSection persistSection(Project project, ProjectSectionStatus status) {
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .title("문제 정의")
                .sectionOrder(1)
                .status(status)
                .build();
        entityManager.persist(section);
        entityManager.flush();
        return section;
    }

    private void persistSubmittedOpinion(ProjectSection section, User author) {
        Opinion opinion = Opinion.builder()
                .projectSection(section)
                .author(author)
                .content("타겟을 공모전 참가 대학생 팀으로 좁히는 게 좋겠습니다.")
                .status(OpinionStatus.DRAFT)
                .build();
        opinion.submit(SUBMITTED_AT);
        entityManager.persist(opinion);
        entityManager.flush();
    }

    private void persistDraftOpinion(ProjectSection section, User author) {
        entityManager.persist(Opinion.builder()
                .projectSection(section)
                .author(author)
                .content("아직 정리 중인 초안입니다.")
                .status(OpinionStatus.DRAFT)
                .build());
        entityManager.flush();
    }
}
