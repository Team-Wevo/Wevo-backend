package com.wevo.backend.section;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.review.domain.TeamReview;
import com.wevo.backend.review.domain.TeamReviewStatus;
import com.wevo.backend.section.domain.DraftLease;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확정 가능 여부 조회(§3.7.5)를 실제 PostgreSQL 컨텍스트로 검증한다.
 *
 * <p>§6.3 조건 5개 각각의 미충족 케이스, 팀원의 확정 시도(canConfirm=false), 1인 프로젝트 예외(§6.3.1),
 * 만료/해소된 검토의 판정 제외를 전 구간(컨트롤러→서비스→리포지토리)으로 확인한다.
 *
 * <p>{@code checks[]} 순서는 서비스가 고정한다: [0]SECTION_REVIEWING [1]AI_CHECK_CURRENT
 * [2]MEMBER_APPROVED [3]NO_UNRESOLVED_REQUEST [4]NO_ACTIVE_EDITOR. 인덱스로 단언하되 key도 함께 검증한다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
@AutoConfigureMockMvc
@Transactional
class SectionConfirmReadinessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("모든 조건 충족 + 팀장 조회 → ready·canConfirm 모두 true, 충족 항목엔 reason 없음")
    void allSatisfied_owner() throws Exception {
        User owner = persistUser("cr-own1@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User member = persistUser("cr-mem1@wevo.com");
        persistMember(project, member, ProjectMemberRole.MEMBER);
        ProjectSection section = reviewingSectionWithCurrentAiCheck(project);
        persistReview(section, member, TeamReviewStatus.APPROVED, false, false);
        em.flush();

        getReadiness(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.canConfirm").value(true))
                .andExpect(jsonPath("$.data.checks.length()").value(5))
                .andExpect(jsonPath("$.data.checks[0].key").value("SECTION_REVIEWING"))
                .andExpect(jsonPath("$.data.checks[0].satisfied").value(true))
                .andExpect(jsonPath("$.data.checks[0].reason").doesNotExist())
                .andExpect(jsonPath("$.data.checks[1].satisfied").value(true))
                .andExpect(jsonPath("$.data.checks[2].satisfied").value(true))
                .andExpect(jsonPath("$.data.checks[3].satisfied").value(true))
                .andExpect(jsonPath("$.data.checks[4].satisfied").value(true));
    }

    @Test
    @DisplayName("모든 조건 충족 + 팀원 조회 → ready=true 지만 canConfirm=false (팀장만 확정)")
    void allSatisfied_member_cannotConfirm() throws Exception {
        User owner = persistUser("cr-own2@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User member = persistUser("cr-mem2@wevo.com");
        persistMember(project, member, ProjectMemberRole.MEMBER);
        ProjectSection section = reviewingSectionWithCurrentAiCheck(project);
        persistReview(section, member, TeamReviewStatus.APPROVED, false, false);
        em.flush();

        getReadiness(section.getId(), member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.canConfirm").value(false));
    }

    @Test
    @DisplayName("섹션이 REVIEWING 이 아니면 SECTION_REVIEWING 미충족 + 사유, ready=false")
    void sectionNotReviewing_unsatisfied() throws Exception {
        User owner = persistUser("cr-own3@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project, ProjectSectionStatus.DRAFTING);
        persistDraft(section, "v1", 1, owner);
        section.bindCurrentAiCheck();
        em.flush();

        getReadiness(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.canConfirm").value(false))
                .andExpect(jsonPath("$.data.checks[0].key").value("SECTION_REVIEWING"))
                .andExpect(jsonPath("$.data.checks[0].satisfied").value(false))
                .andExpect(jsonPath("$.data.checks[0].reason").value("섹션이 검토 단계가 아닙니다."));
    }

    @Test
    @DisplayName("AI 사전 검토가 없으면(null) AI_CHECK_CURRENT 미충족 + §6.3.2 사유")
    void aiCheckMissing_unsatisfied() throws Exception {
        User owner = persistUser("cr-own4@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        // aiCheck 를 바인딩하지 않아 null (성공한 사전 검토 없음)
        ProjectSection section = persistSection(project, ProjectSectionStatus.REVIEWING);
        persistDraft(section, "v1", 1, owner);
        em.flush();

        getReadiness(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checks[1].key").value("AI_CHECK_CURRENT"))
                .andExpect(jsonPath("$.data.checks[1].satisfied").value(false))
                .andExpect(jsonPath("$.data.checks[1].reason")
                        .value("현재 초안에 대한 AI 사전 검토가 필요합니다."));
    }

    @Test
    @DisplayName("AI 사전 검토가 OUTDATED 여도 AI_CHECK_CURRENT 미충족")
    void aiCheckOutdated_unsatisfied() throws Exception {
        User owner = persistUser("cr-own4b@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project, ProjectSectionStatus.REVIEWING);
        persistDraft(section, "v1", 1, owner);
        section.bindCurrentAiCheck();
        section.markAiCheckOutdated(); // CURRENT → OUTDATED
        em.flush();

        getReadiness(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checks[1].satisfied").value(false));
    }

    @Test
    @DisplayName("팀원 동의가 없으면 MEMBER_APPROVED 미충족 + §6.3.2 사유")
    void noApproval_unsatisfied() throws Exception {
        User owner = persistUser("cr-own5@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User member = persistUser("cr-mem5@wevo.com");
        persistMember(project, member, ProjectMemberRole.MEMBER);
        ProjectSection section = reviewingSectionWithCurrentAiCheck(project);
        // 팀원이 아직 아무도 동의하지 않음(검토 미제출)
        em.flush();

        getReadiness(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.checks[2].key").value("MEMBER_APPROVED"))
                .andExpect(jsonPath("$.data.checks[2].satisfied").value(false))
                .andExpect(jsonPath("$.data.checks[2].reason").value("팀원 1명 이상의 동의가 필요합니다."));
    }

    @Test
    @DisplayName("만료(outdated)된 동의만 있으면 MEMBER_APPROVED 미충족 — 이전 본문 대상이라 세지 않음")
    void onlyOutdatedApproval_unsatisfied() throws Exception {
        User owner = persistUser("cr-own6@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User member = persistUser("cr-mem6@wevo.com");
        persistMember(project, member, ProjectMemberRole.MEMBER);
        ProjectSection section = reviewingSectionWithCurrentAiCheck(project);
        persistReview(section, member, TeamReviewStatus.APPROVED, false, true); // outdated APPROVED
        em.flush();

        getReadiness(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checks[2].satisfied").value(false));
    }

    @Test
    @DisplayName("미해결 수정요청이 있으면 NO_UNRESOLVED_REQUEST 미충족 + §6.3.2 사유")
    void unresolvedChangeRequest_unsatisfied() throws Exception {
        User owner = persistUser("cr-own7@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User approver = persistUser("cr-app7@wevo.com");
        persistMember(project, approver, ProjectMemberRole.MEMBER);
        User requester = persistUser("cr-req7@wevo.com");
        persistMember(project, requester, ProjectMemberRole.MEMBER);
        ProjectSection section = reviewingSectionWithCurrentAiCheck(project);
        persistReview(section, approver, TeamReviewStatus.APPROVED, false, false);          // 동의 1명
        persistReview(section, requester, TeamReviewStatus.CHANGES_REQUESTED, false, false); // 미해결 수정요청
        em.flush();

        getReadiness(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.checks[2].satisfied").value(true))  // 동의는 충족
                .andExpect(jsonPath("$.data.checks[3].key").value("NO_UNRESOLVED_REQUEST"))
                .andExpect(jsonPath("$.data.checks[3].satisfied").value(false))
                .andExpect(jsonPath("$.data.checks[3].reason").value("미해결 수정요청이 있습니다."));
    }

    @Test
    @DisplayName("수정요청이 해소(resolved)됐으면 NO_UNRESOLVED_REQUEST 충족 — 확정을 막지 않음")
    void resolvedChangeRequest_satisfied() throws Exception {
        User owner = persistUser("cr-own8@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User approver = persistUser("cr-app8@wevo.com");
        persistMember(project, approver, ProjectMemberRole.MEMBER);
        User requester = persistUser("cr-req8@wevo.com");
        persistMember(project, requester, ProjectMemberRole.MEMBER);
        ProjectSection section = reviewingSectionWithCurrentAiCheck(project);
        persistReview(section, approver, TeamReviewStatus.APPROVED, false, false);
        persistReview(section, requester, TeamReviewStatus.CHANGES_REQUESTED, true, false); // resolved
        em.flush();

        getReadiness(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.checks[3].satisfied").value(true));
    }

    @Test
    @DisplayName("활성 편집자가 있으면 NO_ACTIVE_EDITOR 미충족 + §6.3.2 사유")
    void activeEditor_unsatisfied() throws Exception {
        User owner = persistUser("cr-own9@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User member = persistUser("cr-mem9@wevo.com");
        persistMember(project, member, ProjectMemberRole.MEMBER);
        ProjectSection section = reviewingSectionWithCurrentAiCheck(project);
        persistReview(section, member, TeamReviewStatus.APPROVED, false, false);
        persistActiveLease(section, member); // 편집 중
        em.flush();

        getReadiness(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.checks[4].key").value("NO_ACTIVE_EDITOR"))
                .andExpect(jsonPath("$.data.checks[4].satisfied").value(false))
                .andExpect(jsonPath("$.data.checks[4].reason").value("현재 초안을 편집 중인 사용자가 있습니다."));
    }

    @Test
    @DisplayName("만료된 편집권은 편집자로 보지 않아 NO_ACTIVE_EDITOR 충족")
    void expiredLease_satisfied() throws Exception {
        User owner = persistUser("cr-own9b@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User member = persistUser("cr-mem9b@wevo.com");
        persistMember(project, member, ProjectMemberRole.MEMBER);
        ProjectSection section = reviewingSectionWithCurrentAiCheck(project);
        persistReview(section, member, TeamReviewStatus.APPROVED, false, false);
        persistLease(section, member, LocalDateTime.now().minusMinutes(1)); // 만료됨
        em.flush();

        getReadiness(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.checks[4].satisfied").value(true));
    }

    @Test
    @DisplayName("1인 프로젝트(팀장 1명)면 검토가 없어도 MEMBER_APPROVED 충족 (§6.3.1)")
    void soloProject_memberApprovedSatisfied() throws Exception {
        User owner = persistUser("cr-solo@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER); // 멤버가 팀장뿐
        ProjectSection section = reviewingSectionWithCurrentAiCheck(project);
        em.flush();

        getReadiness(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.canConfirm").value(true))
                .andExpect(jsonPath("$.data.checks[2].key").value("MEMBER_APPROVED"))
                .andExpect(jsonPath("$.data.checks[2].satisfied").value(true))
                .andExpect(jsonPath("$.data.checks[2].reason").doesNotExist());
    }

    @Test
    @DisplayName("모든 조건이 동시에 미충족이면 5개 전부 satisfied=false, ready=false")
    void allUnsatisfied() throws Exception {
        User owner = persistUser("cr-allbad@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User member = persistUser("cr-allbad-m@wevo.com");
        persistMember(project, member, ProjectMemberRole.MEMBER);
        // DRAFTING(REVIEWING 아님) + aiCheck null + 동의 없음 + 미해결 수정요청 + 활성 편집자
        ProjectSection section = persistSection(project, ProjectSectionStatus.DRAFTING);
        persistDraft(section, "v1", 1, owner);
        persistReview(section, member, TeamReviewStatus.CHANGES_REQUESTED, false, false);
        persistActiveLease(section, member);
        em.flush();

        getReadiness(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.checks[0].satisfied").value(false))
                .andExpect(jsonPath("$.data.checks[1].satisfied").value(false))
                .andExpect(jsonPath("$.data.checks[2].satisfied").value(false))
                .andExpect(jsonPath("$.data.checks[3].satisfied").value(false))
                .andExpect(jsonPath("$.data.checks[4].satisfied").value(false));
    }

    @Test
    @DisplayName("프로젝트 참여자가 아니면 404(S001) — 섹션 존재를 숨긴다")
    void nonMember_hiddenAsNotFound() throws Exception {
        User owner = persistUser("cr-own10@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = reviewingSectionWithCurrentAiCheck(project);
        User outsider = persistUser("cr-out10@wevo.com");
        em.flush();

        getReadiness(section.getId(), outsider)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S001"));
    }

    @Test
    @DisplayName("인증 없이 조회하면 401(A001)")
    void withoutAuthentication_returnsA001() throws Exception {
        mockMvc.perform(get("/api/project-sections/{id}/confirm-readiness", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));
    }

    // --- 헬퍼 ---

    private ResultActions getReadiness(Long sectionId, User user) throws Exception {
        return mockMvc.perform(get("/api/project-sections/{id}/confirm-readiness", sectionId)
                .with(authentication(new UsernamePasswordAuthenticationToken(
                        new AuthPrincipal(user.getId()), null, AuthorityUtils.NO_AUTHORITIES))));
    }

    /** REVIEWING + 초안 + aiCheck CURRENT 로 다른 조건이 충족된 섹션. (동의·편집자만 테스트별로 변경) */
    private ProjectSection reviewingSectionWithCurrentAiCheck(Project project) {
        ProjectSection section = persistSection(project, ProjectSectionStatus.REVIEWING);
        persistDraft(section, "검토 대상 v1", 1, project.getOwner());
        section.bindCurrentAiCheck();
        return section;
    }

    private User persistUser(String email) {
        User user = User.builder().name(email.substring(0, email.indexOf('@')))
                .email(email).status(UserStatus.ACTIVE).build();
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

    private ProjectMember persistMember(Project project, User user, ProjectMemberRole role) {
        ProjectMember member = ProjectMember.builder()
                .project(project).user(user).role(role).joinedAt(LocalDateTime.now()).build();
        em.persist(member);
        return member;
    }

    private ProjectSection persistSection(Project project, ProjectSectionStatus status) {
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .title("문제 정의")
                .sectionOrder(1)
                .status(status)
                .build();
        em.persist(section);
        return section;
    }

    private void persistDraft(ProjectSection section, String content, int version, User editor) {
        SectionDraft draft = SectionDraft.builder()
                .projectSection(section)
                .content(content)
                .version(version)
                .lastEditor(editor)
                .build();
        em.persist(draft);
    }

    private TeamReview persistReview(ProjectSection section, User reviewer, TeamReviewStatus status,
                                     boolean resolved, boolean outdated) {
        TeamReview review = TeamReview.builder()
                .projectSection(section)
                .reviewer(reviewer)
                .status(status)
                .changeRequestReason(status == TeamReviewStatus.CHANGES_REQUESTED ? "수정 사유" : null)
                .reviewedContentVersion(1)
                .build();
        if (resolved) {
            review.updateResolved(true);
        }
        if (outdated) {
            review.markOutdated();
        }
        em.persist(review);
        return review;
    }

    private DraftLease persistActiveLease(ProjectSection section, User holder) {
        return persistLease(section, holder, LocalDateTime.now().plusHours(1));
    }

    private DraftLease persistLease(ProjectSection section, User holder, LocalDateTime leaseUntil) {
        DraftLease lease = DraftLease.builder()
                .projectSection(section)
                .holderUserId(holder.getId())
                .leaseUntil(leaseUntil)
                .build();
        em.persist(lease);
        return lease;
    }
}
