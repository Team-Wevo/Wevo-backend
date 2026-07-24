package com.wevo.backend.section;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.wevo.backend.section.domain.DriftStatus;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.assertj.core.api.Assertions;
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
 * 섹션 확정(§3.7.6)을 실제 PostgreSQL 컨텍스트로 검증한다.
 *
 * <p>§6.3 조건을 서버가 재검증한 뒤 {@code REVIEWING → CONFIRMED} 전이 · 확정 본문 버전 기록 ·
 * 드리프트 해소가 이뤄지는지, 상태 게이트(S002)와 조건 미충족(C003)이 명세대로 갈리는지,
 * OWNER 아닌 접근(A002)·존재 숨김(S001)을 전 구간(컨트롤러→서비스→리포지토리)으로 확인한다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
@AutoConfigureMockMvc
@Transactional
class SectionConfirmIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("모든 조건 충족 + 팀장 → 200 SECTION_CONFIRMED, 상태 CONFIRMED·확정 버전 기록")
    void allSatisfied_owner_confirms() throws Exception {
        User owner = persistUser("cf-own1@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User member = persistUser("cf-mem1@wevo.com");
        persistMember(project, member, ProjectMemberRole.MEMBER);
        ProjectSection section = reviewingSectionWithCurrentAiCheck(project);
        persistReview(section, member, TeamReviewStatus.APPROVED, false, false);
        em.flush();

        confirm(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SECTION_CONFIRMED"))
                .andExpect(jsonPath("$.data.sectionId").value(section.getId()))
                .andExpect(jsonPath("$.data.sectionStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedVersion").value(1));

        em.flush();
        em.clear();
        ProjectSection reloaded = em.find(ProjectSection.class, section.getId());
        Assertions.assertThat(reloaded.getStatus()).isEqualTo(ProjectSectionStatus.CONFIRMED);
        Assertions.assertThat(reloaded.getConfirmedVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("드리프트(REVIEW_REQUIRED) 섹션도 조건을 충족하면 확정되며 드리프트가 해소된다 (§6.4)")
    void driftedSection_confirm_clearsDrift() throws Exception {
        User owner = persistUser("cf-own2@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User member = persistUser("cf-mem2@wevo.com");
        persistMember(project, member, ProjectMemberRole.MEMBER);
        ProjectSection section = reviewingSectionWithCurrentAiCheck(project);
        section.markDriftReviewRequired();
        persistReview(section, member, TeamReviewStatus.APPROVED, false, false);
        em.flush();

        confirm(section.getId(), owner).andExpect(status().isOk());

        em.flush();
        em.clear();
        ProjectSection reloaded = em.find(ProjectSection.class, section.getId());
        Assertions.assertThat(reloaded.getStatus()).isEqualTo(ProjectSectionStatus.CONFIRMED);
        Assertions.assertThat(reloaded.getDriftStatus()).isEqualTo(DriftStatus.NONE);
    }

    @Test
    @DisplayName("팀원(MEMBER)의 확정 시도는 403 A002")
    void member_cannotConfirm_returnsA002() throws Exception {
        User owner = persistUser("cf-own3@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User member = persistUser("cf-mem3@wevo.com");
        persistMember(project, member, ProjectMemberRole.MEMBER);
        ProjectSection section = reviewingSectionWithCurrentAiCheck(project);
        persistReview(section, member, TeamReviewStatus.APPROVED, false, false);
        em.flush();

        confirm(section.getId(), member)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    @Test
    @DisplayName("섹션이 REVIEWING이 아니면 409 S002 (조건 미충족 C003이 아니라 전이 불가)")
    void notReviewing_returnsS002() throws Exception {
        User owner = persistUser("cf-own4@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project, ProjectSectionStatus.DRAFTING);
        persistDraft(section, "v1", 1, owner);
        section.bindCurrentAiCheck();
        em.flush();

        confirm(section.getId(), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S002"));
    }

    @Test
    @DisplayName("팀원 동의가 없으면 409 C003 + MEMBER_APPROVED 사유")
    void noApproval_returnsC003() throws Exception {
        User owner = persistUser("cf-own5@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User member = persistUser("cf-mem5@wevo.com");
        persistMember(project, member, ProjectMemberRole.MEMBER);
        ProjectSection section = reviewingSectionWithCurrentAiCheck(project);
        // 팀원이 아직 동의하지 않음
        em.flush();

        confirm(section.getId(), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("C003"))
                .andExpect(jsonPath("$.errors[?(@.field == 'MEMBER_APPROVED')].reason")
                        .value("팀원 1명 이상의 동의가 필요합니다."));

        // 실패 시 상태가 바뀌지 않는다
        em.flush();
        em.clear();
        Assertions.assertThat(em.find(ProjectSection.class, section.getId()).getStatus())
                .isEqualTo(ProjectSectionStatus.REVIEWING);
    }

    @Test
    @DisplayName("AI 사전 검토가 최신이 아니면 409 C003 + AI_CHECK_CURRENT 사유")
    void aiCheckNotCurrent_returnsC003() throws Exception {
        User owner = persistUser("cf-own8@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User member = persistUser("cf-mem8@wevo.com");
        persistMember(project, member, ProjectMemberRole.MEMBER);
        // aiCheck 를 바인딩하지 않아 null (성공한 사전 검토 없음). 나머지 조건은 충족.
        ProjectSection section = persistSection(project, ProjectSectionStatus.REVIEWING);
        persistDraft(section, "검토 대상 v1", 1, owner);
        persistReview(section, member, TeamReviewStatus.APPROVED, false, false);
        em.flush();

        confirm(section.getId(), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("C003"))
                .andExpect(jsonPath("$.errors[?(@.field == 'AI_CHECK_CURRENT')].reason")
                        .value("현재 초안에 대한 AI 사전 검토가 필요합니다."));
    }

    @Test
    @DisplayName("미해결 수정요청이 있으면 409 C003 + NO_UNRESOLVED_REQUEST 사유 (동의는 충족)")
    void unresolvedChangeRequest_returnsC003() throws Exception {
        User owner = persistUser("cf-own9@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User approver = persistUser("cf-app9@wevo.com");
        persistMember(project, approver, ProjectMemberRole.MEMBER);
        User requester = persistUser("cf-req9@wevo.com");
        persistMember(project, requester, ProjectMemberRole.MEMBER);
        ProjectSection section = reviewingSectionWithCurrentAiCheck(project);
        persistReview(section, approver, TeamReviewStatus.APPROVED, false, false);           // 동의 1명
        persistReview(section, requester, TeamReviewStatus.CHANGES_REQUESTED, false, false); // 미해결 수정요청
        em.flush();

        confirm(section.getId(), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("C003"))
                .andExpect(jsonPath("$.errors[?(@.field == 'NO_UNRESOLVED_REQUEST')].reason")
                        .value("미해결 수정요청이 있습니다."))
                .andExpect(jsonPath("$.errors[?(@.field == 'MEMBER_APPROVED')]").isEmpty()); // 동의는 충족
    }

    @Test
    @DisplayName("활성 편집자가 있으면 409 C003 + NO_ACTIVE_EDITOR 사유")
    void activeEditor_returnsC003() throws Exception {
        User owner = persistUser("cf-own6@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User member = persistUser("cf-mem6@wevo.com");
        persistMember(project, member, ProjectMemberRole.MEMBER);
        ProjectSection section = reviewingSectionWithCurrentAiCheck(project);
        persistReview(section, member, TeamReviewStatus.APPROVED, false, false);
        persistActiveLease(section, member);
        em.flush();

        confirm(section.getId(), owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("C003"))
                .andExpect(jsonPath("$.errors[?(@.field == 'NO_ACTIVE_EDITOR')].reason")
                        .value("현재 초안을 편집 중인 사용자가 있습니다."));
    }

    @Test
    @DisplayName("프로젝트 참여자가 아니면 404(S001) — 섹션 존재를 숨긴다")
    void nonMember_hiddenAsNotFound() throws Exception {
        User owner = persistUser("cf-own7@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = reviewingSectionWithCurrentAiCheck(project);
        User outsider = persistUser("cf-out7@wevo.com");
        em.flush();

        confirm(section.getId(), outsider)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S001"));
    }

    @Test
    @DisplayName("인증 없이 확정하면 401(A001)")
    void withoutAuthentication_returnsA001() throws Exception {
        mockMvc.perform(post("/api/project-sections/{id}/confirm", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));
    }

    // --- 헬퍼 ---

    private ResultActions confirm(Long sectionId, User user) throws Exception {
        return mockMvc.perform(post("/api/project-sections/{id}/confirm", sectionId)
                .with(authentication(new UsernamePasswordAuthenticationToken(
                        new AuthPrincipal(user.getId()), null, AuthorityUtils.NO_AUTHORITIES))));
    }

    /** REVIEWING + 초안(v1) + aiCheck CURRENT 로 다른 조건이 충족된 섹션. (동의·편집자만 테스트별로 변경) */
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
                .project(project).user(user).role(role).joinedAt(LocalDateTime.now(KST)).build();
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
        DraftLease lease = DraftLease.builder()
                .projectSection(section)
                .holderUserId(holder.getId())
                .leaseUntil(LocalDateTime.now(KST).plusHours(1))
                .build();
        em.persist(lease);
        return lease;
    }
}
