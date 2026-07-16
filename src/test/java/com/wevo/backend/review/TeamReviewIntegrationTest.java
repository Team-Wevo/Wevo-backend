package com.wevo.backend.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.review.service.TeamReviewService;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.review.repository.TeamReviewRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀(내부) 검토 조회·제출 전 경로를 실제 컨텍스트(H2)로 검증한다. (제품 정책서 §6.1)
 *
 * <p>프로젝트/섹션/멤버 생성 API가 아직 없어 EntityManager 로 직접 시드한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeamReviewIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TeamReviewRepository teamReviewRepository;
    @Autowired
    private TeamReviewService teamReviewService;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("팀원이 동의하면 동의수 N/M 이 정확히 집계되고, 미제출 팀원은 PENDING 으로 노출된다")
    void memberApprovesAndCountsAreAccurate() throws Exception {
        User owner = persistUser("owner@team.com");
        Project project = persistProject(owner);
        ProjectSection section = persistReviewingSectionWithDraft(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User m1 = persistUser("m1@team.com");
        User m2 = persistUser("m2@team.com");
        persistMember(project, m1, ProjectMemberRole.MEMBER);
        persistMember(project, m2, ProjectMemberRole.MEMBER);
        em.flush();

        // m1 동의
        submit(section.getId(), m1, "APPROVED", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TEAM_REVIEW_SUBMITTED"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // 현황: 분모 M=2(팀원 수, 팀장 제외), 동의 1, m2 는 PENDING
        mockMvc.perform(get("/api/project-sections/{id}/team-reviews", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMembers").value(2))
                .andExpect(jsonPath("$.data.approvedCount").value(1))
                .andExpect(jsonPath("$.data.pendingCount").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    @DisplayName("팀장(OWNER)은 검토를 제출할 수 없다 (403)")
    void ownerCannotSubmit() throws Exception {
        User owner = persistUser("owner2@team.com");
        Project project = persistProject(owner);
        ProjectSection section = persistReviewingSectionWithDraft(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.flush();

        submit(section.getId(), owner, "APPROVED", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("수정 요청은 사유가 없으면 400(C001)")
    void changesRequestedRequiresReason() throws Exception {
        User owner = persistUser("owner3@team.com");
        Project project = persistProject(owner);
        ProjectSection section = persistReviewingSectionWithDraft(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User m1 = persistUser("m3@team.com");
        persistMember(project, m1, ProjectMemberRole.MEMBER);
        em.flush();

        // 사유 없이 CHANGES_REQUESTED → 400
        submit(section.getId(), m1, "CHANGES_REQUESTED", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        // 사유 포함 → 200
        submit(section.getId(), m1, "CHANGES_REQUESTED", "3번째 문단 근거가 부족합니다.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.data.changeRequestReason").value("3번째 문단 근거가 부족합니다."));
    }

    @Test
    @DisplayName("같은 팀원이 다시 제출하면 새 행이 아니라 기존 검토가 갱신된다 (1인 1검토)")
    void resubmitUpdatesInPlace() throws Exception {
        User owner = persistUser("owner4@team.com");
        Project project = persistProject(owner);
        ProjectSection section = persistReviewingSectionWithDraft(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User m1 = persistUser("m4@team.com");
        persistMember(project, m1, ProjectMemberRole.MEMBER);
        em.flush();

        submit(section.getId(), m1, "APPROVED", null).andExpect(status().isOk());
        submit(section.getId(), m1, "CHANGES_REQUESTED", "역시 수정이 필요합니다.").andExpect(status().isOk());

        assertThat(teamReviewRepository.findByProjectSection_Id(section.getId())).hasSize(1);
        mockMvc.perform(get("/api/project-sections/{id}/team-reviews", section.getId())
                        .with(authentication(authOf(m1))))
                .andExpect(jsonPath("$.data.approvedCount").value(0))
                .andExpect(jsonPath("$.data.changesRequestedCount").value(1));
    }

    @Test
    @DisplayName("검토 단계(REVIEWING)가 아닌 섹션에는 제출할 수 없다 (422 R006)")
    void cannotSubmitWhenNotReviewing() throws Exception {
        User owner = persistUser("owner5@team.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project, ProjectSectionStatus.DRAFTING);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User m1 = persistUser("m5@team.com");
        persistMember(project, m1, ProjectMemberRole.MEMBER);
        em.flush();

        submit(section.getId(), m1, "APPROVED", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("R006"));
    }

    @Test
    @DisplayName("프로젝트 멤버가 아니면 현황 조회 시 403(P002)")
    void nonMemberCannotView() throws Exception {
        User owner = persistUser("owner6@team.com");
        Project project = persistProject(owner);
        ProjectSection section = persistReviewingSectionWithDraft(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User outsider = persistUser("outsider@team.com");
        em.flush();

        mockMvc.perform(get("/api/project-sections/{id}/team-reviews", section.getId())
                        .with(authentication(authOf(outsider))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("P002"));
    }

    @Test
    @DisplayName("팀장이 수정 요청을 resolved 처리하면 resolved=true 가 된다")
    void ownerResolvesChangeRequest() throws Exception {
        User owner = persistUser("owner-r1@team.com");
        Project project = persistProject(owner);
        ProjectSection section = persistReviewingSectionWithDraft(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User m1 = persistUser("mr1@team.com");
        persistMember(project, m1, ProjectMemberRole.MEMBER);
        em.flush();

        submit(section.getId(), m1, "CHANGES_REQUESTED", "근거 부족").andExpect(status().isOk());
        Long reviewId = reviewIdOf(section.getId(), m1);

        resolve(section.getId(), reviewId, owner, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TEAM_REVIEW_RESOLVED"))
                .andExpect(jsonPath("$.data.resolved").value(true))
                .andExpect(jsonPath("$.data.status").value("CHANGES_REQUESTED"));
    }

    @Test
    @DisplayName("팀원(MEMBER)은 수정 요청을 resolve 할 수 없다 (403)")
    void memberCannotResolve() throws Exception {
        User owner = persistUser("owner-r2@team.com");
        Project project = persistProject(owner);
        ProjectSection section = persistReviewingSectionWithDraft(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User m1 = persistUser("mr2@team.com");
        persistMember(project, m1, ProjectMemberRole.MEMBER);
        em.flush();

        submit(section.getId(), m1, "CHANGES_REQUESTED", "근거 부족").andExpect(status().isOk());
        Long reviewId = reviewIdOf(section.getId(), m1);

        resolve(section.getId(), reviewId, m1, true)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("동의(APPROVED) 검토는 resolve 할 수 없다 (422 R008)")
    void cannotResolveApproved() throws Exception {
        User owner = persistUser("owner-r3@team.com");
        Project project = persistProject(owner);
        ProjectSection section = persistReviewingSectionWithDraft(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User m1 = persistUser("mr3@team.com");
        persistMember(project, m1, ProjectMemberRole.MEMBER);
        em.flush();

        submit(section.getId(), m1, "APPROVED", null).andExpect(status().isOk());
        Long reviewId = reviewIdOf(section.getId(), m1);

        resolve(section.getId(), reviewId, owner, true)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("R008"));
    }

    @Test
    @DisplayName("존재하지 않는 검토를 resolve 하면 404(R007)")
    void resolveUnknownReview() throws Exception {
        User owner = persistUser("owner-r4@team.com");
        Project project = persistProject(owner);
        ProjectSection section = persistReviewingSectionWithDraft(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.flush();

        resolve(section.getId(), 999_999L, owner, true)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R007"));
    }

    @Test
    @DisplayName("본문 수정으로 만료되면 동의는 outdated 로 빠지고 outdatedCount 로 잡힌다")
    void contentEditOutdatesReviews() throws Exception {
        User owner = persistUser("owner-o1@team.com");
        Project project = persistProject(owner);
        ProjectSection section = persistReviewingSectionWithDraft(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User m1 = persistUser("mo1@team.com");
        persistMember(project, m1, ProjectMemberRole.MEMBER);
        em.flush();

        submit(section.getId(), m1, "APPROVED", null).andExpect(status().isOk());

        // 본문 저장 플로우가 호출할 만료 처리
        teamReviewService.markSectionTeamReviewsOutdated(section.getId());

        mockMvc.perform(get("/api/project-sections/{id}/team-reviews", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvedCount").value(0))
                .andExpect(jsonPath("$.data.outdatedCount").value(1))
                .andExpect(jsonPath("$.data.items[0].outdated").value(true));
    }

    @Test
    @DisplayName("만료 후 재검토하면 outdated 가 다시 꺼지고 동의로 잡힌다")
    void resubmitClearsOutdated() throws Exception {
        User owner = persistUser("owner-o2@team.com");
        Project project = persistProject(owner);
        ProjectSection section = persistReviewingSectionWithDraft(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User m1 = persistUser("mo2@team.com");
        persistMember(project, m1, ProjectMemberRole.MEMBER);
        em.flush();

        submit(section.getId(), m1, "APPROVED", null).andExpect(status().isOk());
        teamReviewService.markSectionTeamReviewsOutdated(section.getId());

        // 팀원이 새 본문으로 다시 동의 → outdated 해제
        submit(section.getId(), m1, "APPROVED", null).andExpect(status().isOk());

        mockMvc.perform(get("/api/project-sections/{id}/team-reviews", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(jsonPath("$.data.approvedCount").value(1))
                .andExpect(jsonPath("$.data.outdatedCount").value(0));
    }

    // --- 헬퍼 ---

    private org.springframework.test.web.servlet.ResultActions resolve(
            Long sectionId, Long reviewId, User user, boolean resolved) throws Exception {
        return mockMvc.perform(patch("/api/project-sections/{sid}/team-reviews/{rid}", sectionId, reviewId)
                .with(authentication(authOf(user)))
                .contentType("application/json")
                .content("{ \"resolved\": %s }".formatted(resolved)));
    }

    private Long reviewIdOf(Long sectionId, User reviewer) {
        return teamReviewRepository
                .findByProjectSection_IdAndReviewer_Id(sectionId, reviewer.getId())
                .orElseThrow()
                .getId();
    }

    private org.springframework.test.web.servlet.ResultActions submit(
            Long sectionId, User user, String status, String reason) throws Exception {
        String body = reason == null
                ? "{ \"status\": \"%s\" }".formatted(status)
                : "{ \"status\": \"%s\", \"changeRequestReason\": \"%s\" }".formatted(status, reason);
        return mockMvc.perform(put("/api/project-sections/{id}/team-reviews/me", sectionId)
                .with(authentication(authOf(user)))
                .contentType("application/json")
                .content(body));
    }

    private UsernamePasswordAuthenticationToken authOf(User user) {
        return new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(user.getId()), null, AuthorityUtils.NO_AUTHORITIES);
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
                .needsReReview(false)
                .build();
        em.persist(section);
        return section;
    }

    private ProjectSection persistReviewingSectionWithDraft(Project project) {
        ProjectSection section = persistSection(project, ProjectSectionStatus.REVIEWING);
        SectionDraft draft = SectionDraft.builder()
                .projectSection(section)
                .content("초안 본문")
                .version(1)
                .build();
        em.persist(draft);
        return section;
    }
}
