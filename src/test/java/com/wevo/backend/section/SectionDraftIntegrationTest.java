package com.wevo.backend.section;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.review.domain.ReviewLink;
import com.wevo.backend.review.domain.ReviewLinkStatus;
import com.wevo.backend.review.domain.TeamReview;
import com.wevo.backend.review.domain.TeamReviewStatus;
import com.wevo.backend.review.repository.ReviewLinkRepository;
import com.wevo.backend.review.repository.TeamReviewRepository;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.repository.SectionDraftRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 섹션 초안 저장 전 경로를 실제 컨텍스트(H2)로 검증한다. (API_SPEC §1.9)
 *
 * <p>프로젝트/섹션 생성 API가 아직 없어 EntityManager 로 직접 시드한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SectionDraftIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SectionDraftRepository sectionDraftRepository;
    @Autowired
    private TeamReviewRepository teamReviewRepository;
    @Autowired
    private ReviewLinkRepository reviewLinkRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("초안이 없는 DRAFTING 섹션에 첫 저장하면 version 1 이 발급된다")
    void firstSaveIssuesVersionOne() throws Exception {
        User owner = persistUser("owner-d1@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project, ProjectSectionStatus.DRAFTING);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.flush();

        save(section.getId(), owner, "첫 초안 본문", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DRAFT_SAVED"))
                .andExpect(jsonPath("$.data.contentVersion").value(1))
                .andExpect(jsonPath("$.data.sectionStatus").value("DRAFTING"));

        assertThat(sectionDraftRepository.findTopByProjectSection_IdOrderByVersionDesc(section.getId())
                .orElseThrow().getContent()).isEqualTo("첫 초안 본문");
    }

    @Test
    @DisplayName("최신 버전 위에 저장하면 version 이 증가한다 (append 이력)")
    void saveAppendsNewVersion() throws Exception {
        User member = persistUser("m-d2@wevo.com");
        Project project = persistProject(member);
        ProjectSection section = persistSection(project, ProjectSectionStatus.DRAFTING);
        persistMember(project, member, ProjectMemberRole.MEMBER);
        persistDraft(section, "v1 본문", 1, member);
        em.flush();

        save(section.getId(), member, "v2 본문", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentVersion").value(2));

        // 이력 보존 — 이전 버전 행이 남는다
        assertThat(sectionDraftRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("기준 버전(baseVersion)이 최신과 다르면 409(C003) 로 덮어쓰기를 막는다")
    void staleBaseVersionRejected() throws Exception {
        User owner = persistUser("owner-d3@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project, ProjectSectionStatus.DRAFTING);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistDraft(section, "v1 본문", 1, owner);
        em.flush();

        // 최신은 1인데 0을 기준으로 보냄 → 충돌
        save(section.getId(), owner, "덮어쓰기 시도", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("C003"));

        assertThat(sectionDraftRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("초안이 없는 단계(COLLECTING)에서 저장하면 409(S002)")
    void notEditableStageRejected() throws Exception {
        User owner = persistUser("owner-d4@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project, ProjectSectionStatus.COLLECTING);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.flush();

        save(section.getId(), owner, "본문", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S002"));

        assertThat(sectionDraftRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("프로젝트 참여자가 아니면 저장 시 404(S001) — 섹션 존재를 숨긴다")
    void nonMemberHiddenAsNotFound() throws Exception {
        User owner = persistUser("owner-d5@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project, ProjectSectionStatus.DRAFTING);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User outsider = persistUser("outsider-d5@wevo.com");
        em.flush();

        // 비멤버는 "섹션 없음"과 구분되지 않아야 한다 (CLAUDE.md §5.6 · API_SPEC §3.7)
        save(section.getId(), outsider, "본문", 0)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S001"));
    }

    @Test
    @DisplayName("공백 본문은 400(C001) 으로 거부한다 — 초안 도메인 불변식")
    void blankContentRejected() throws Exception {
        User owner = persistUser("owner-d8@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project, ProjectSectionStatus.DRAFTING);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.flush();

        save(section.getId(), owner, "   ", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        assertThat(sectionDraftRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("본문이 상한(10,000자)을 넘으면 400(C001) 으로 거부한다")
    void tooLongContentRejected() throws Exception {
        User owner = persistUser("owner-d9@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project, ProjectSectionStatus.DRAFTING);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.flush();

        save(section.getId(), owner, "가".repeat(10_001), 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        assertThat(sectionDraftRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 섹션에 저장하면 404(S001)")
    void unknownSection() throws Exception {
        User owner = persistUser("owner-d6@wevo.com");
        em.flush();

        save(999_999L, owner, "본문", 0)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S001"));
    }

    @Test
    @DisplayName("본문 저장의 부수효과로 팀 검토와 ACTIVE 외부 링크가 만료된다")
    void saveOutdatesReviewsAndLinks() throws Exception {
        User owner = persistUser("owner-d7@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project, ProjectSectionStatus.REVIEWING);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User m1 = persistUser("m-d7@wevo.com");
        persistMember(project, m1, ProjectMemberRole.MEMBER);
        persistDraft(section, "검토 대상 v1", 1, owner);

        TeamReview review = persistTeamReview(section, m1);
        ReviewLink link = persistActiveLink(section, owner);
        em.flush();

        save(section.getId(), owner, "수정한 v2", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentVersion").value(2));

        em.flush();
        em.clear();
        assertThat(teamReviewRepository.findById(review.getId()).orElseThrow().isOutdated()).isTrue();
        assertThat(reviewLinkRepository.findById(link.getId()).orElseThrow().getStatus())
                .isEqualTo(ReviewLinkStatus.OUTDATED);
    }

    @Test
    @DisplayName("직전과 같은 본문을 저장하면 버전이 오르지 않고 검토도 만료되지 않는다")
    void identicalContentDoesNotBumpVersionNorOutdateReviews() throws Exception {
        User owner = persistUser("owner-d10@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project, ProjectSectionStatus.REVIEWING);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User m1 = persistUser("m-d10@wevo.com");
        persistMember(project, m1, ProjectMemberRole.MEMBER);
        persistDraft(section, "그대로인 본문", 1, owner);

        TeamReview review = persistTeamReview(section, m1);
        ReviewLink link = persistActiveLink(section, owner);
        em.flush();

        // 고쳤다 되돌린 뒤 저장 → 내용은 v1 과 동일
        save(section.getId(), owner, "그대로인 본문", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentVersion").value(1));

        em.flush();
        em.clear();
        // 새 버전 행이 생기지 않는다
        assertThat(sectionDraftRepository.findAll()).hasSize(1);
        // 팀 동의와 외부 링크가 그대로 유지된다
        assertThat(teamReviewRepository.findById(review.getId()).orElseThrow().isOutdated()).isFalse();
        assertThat(reviewLinkRepository.findById(link.getId()).orElseThrow().getStatus())
                .isEqualTo(ReviewLinkStatus.ACTIVE);
    }

    // --- 헬퍼 ---

    private ResultActions save(Long sectionId, User user, String content, int baseVersion) throws Exception {
        String body = "{ \"content\": \"%s\", \"baseVersion\": %d }".formatted(content, baseVersion);
        return mockMvc.perform(put("/api/project-sections/{id}/draft", sectionId)
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

    private TeamReview persistTeamReview(ProjectSection section, User reviewer) {
        TeamReview review = TeamReview.builder()
                .projectSection(section)
                .reviewer(reviewer)
                .status(TeamReviewStatus.APPROVED)
                .reviewedContentVersion(1)
                .build();
        em.persist(review);
        return review;
    }

    private ReviewLink persistActiveLink(ProjectSection section, User createdBy) {
        ReviewLink link = ReviewLink.builder()
                .projectSection(section)
                .createdBy(createdBy)
                .tokenHash("hash-" + section.getId())
                .sectionTitleSnapshot(section.getTitle())
                .contentSnapshot("검토 대상 v1")
                .contentVersion(1)
                .status(ReviewLinkStatus.ACTIVE)
                .build();
        em.persist(link);
        return link;
    }
}
