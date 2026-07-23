package com.wevo.backend.section;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
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
import com.wevo.backend.section.domain.AiCheckStatus;
import com.wevo.backend.section.domain.DraftLease;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.repository.DraftLeaseRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 섹션 초안 저장 전 경로를 실제 PostgreSQL 컨텍스트로 검증한다. (API_SPEC §1.9)
 *
 * <p>프로젝트/섹션 생성 API가 아직 없어 EntityManager 로 직접 시드한다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
@AutoConfigureMockMvc
@Transactional
class SectionDraftIntegrationTest {

    // 서비스가 편집권 만료를 Asia/Seoul 기준으로 판정하므로 시드도 같은 시간대로 맞춘다
    // (CI/JVM 기본 시간대가 UTC여도 활성 lease가 만료로 오판되지 않도록).
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SectionDraftRepository sectionDraftRepository;
    @Autowired
    private TeamReviewRepository teamReviewRepository;
    @Autowired
    private ReviewLinkRepository reviewLinkRepository;
    @Autowired
    private DraftLeaseRepository draftLeaseRepository;

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

        // 최초 저장(초안 없음)은 편집권 없이도 가능하다 — 보호할 기존 본문이 없다 (§5.2.1 예외)
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
        persistActiveLease(section, member);
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
        persistActiveLease(section, owner); // 편집권 검사를 통과시켜 baseVersion 충돌을 격리 검증
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
        persistActiveLease(section, owner);
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
        persistActiveLease(section, owner);
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

    @Test
    @DisplayName("본문 저장의 부수효과로 CURRENT 이던 AI 사전 검토가 OUTDATED 로 낡음 처리된다")
    void saveOutdatesCurrentAiCheck() throws Exception {
        User owner = persistUser("owner-d11@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project, ProjectSectionStatus.REVIEWING);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistDraft(section, "검토 대상 v1", 1, owner);
        section.bindCurrentAiCheck(); // aiCheckStatus = CURRENT
        persistActiveLease(section, owner);
        em.flush();

        save(section.getId(), owner, "수정한 v2", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentVersion").value(2));

        em.flush();
        em.clear();
        assertThat(em.find(ProjectSection.class, section.getId()).getAiCheckStatus())
                .isEqualTo(AiCheckStatus.OUTDATED);
    }

    @Test
    @DisplayName("성공한 AI 사전 검토가 없으면(null) 저장해도 낡음 상태를 만들지 않는다")
    void saveDoesNotCreateAiCheckWhenNeverChecked() throws Exception {
        User owner = persistUser("owner-d12@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project, ProjectSectionStatus.DRAFTING);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistDraft(section, "v1 본문", 1, owner);
        persistActiveLease(section, owner);
        em.flush();

        save(section.getId(), owner, "v2 본문", 1)
                .andExpect(status().isOk());

        em.flush();
        em.clear();
        // 검토 이력이 없는 섹션은 "검토 없는데 낡음" 상태가 되지 않는다
        assertThat(em.find(ProjectSection.class, section.getId()).getAiCheckStatus()).isNull();
    }

    @Test
    @DisplayName("직전과 같은 본문을 저장하면 CURRENT 이던 AI 사전 검토가 유지된다")
    void identicalContentKeepsAiCheckCurrent() throws Exception {
        User owner = persistUser("owner-d13@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project, ProjectSectionStatus.REVIEWING);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistDraft(section, "그대로인 본문", 1, owner);
        section.bindCurrentAiCheck(); // aiCheckStatus = CURRENT
        persistActiveLease(section, owner);
        em.flush();

        save(section.getId(), owner, "그대로인 본문", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentVersion").value(1));

        em.flush();
        em.clear();
        assertThat(em.find(ProjectSection.class, section.getId()).getAiCheckStatus())
                .isEqualTo(AiCheckStatus.CURRENT);
    }

    @Test
    @DisplayName("편집권 없이 기존 초안을 저장하면 409(S005) — 미보유")
    void saveExistingDraftWithoutLeaseRejected() throws Exception {
        User owner = persistUser("owner-d14@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project, ProjectSectionStatus.DRAFTING);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistDraft(section, "v1 본문", 1, owner);
        em.flush(); // 편집권 시드 없음

        save(section.getId(), owner, "v2 본문", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S005"));

        // 저장이 막혀 새 버전이 생기지 않는다
        assertThat(sectionDraftRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("타인이 편집권을 보유 중이면 저장 시 409(S004)")
    void saveWhileOtherHoldsLeaseRejected() throws Exception {
        User owner = persistUser("owner-d15@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project, ProjectSectionStatus.DRAFTING);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        User other = persistUser("other-d15@wevo.com");
        persistMember(project, other, ProjectMemberRole.MEMBER);
        persistDraft(section, "v1 본문", 1, owner);
        persistActiveLease(section, other); // 타인이 편집 중
        em.flush();

        save(section.getId(), owner, "v2 본문", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S004"));

        assertThat(sectionDraftRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("만료된 편집권으로 저장하면 409(S005) — 미보유로 본다")
    void saveWithExpiredLeaseRejected() throws Exception {
        User owner = persistUser("owner-d16@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project, ProjectSectionStatus.DRAFTING);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistDraft(section, "v1 본문", 1, owner);
        persistLease(section, owner, LocalDateTime.now(KST).minusMinutes(1)); // 만료됨
        em.flush();

        save(section.getId(), owner, "v2 본문", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S005"));

        assertThat(sectionDraftRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("새 버전 저장에 성공하면 보유하던 편집권이 해제된다 (§5.2.1)")
    void saveReleasesHolderLease() throws Exception {
        User owner = persistUser("owner-d17@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project, ProjectSectionStatus.DRAFTING);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistDraft(section, "v1 본문", 1, owner);
        DraftLease lease = persistActiveLease(section, owner);
        LocalDateTime originalUntil = lease.getLeaseUntil();
        em.flush();

        save(section.getId(), owner, "v2 수정", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentVersion").value(2));

        em.flush();
        em.clear();
        // 해제 시 만료 시각을 현재로 당겨 비활성화한다(행은 남김) — 원래 만료(현재+1시간)보다 앞선다
        assertThat(draftLeaseRepository.findById(lease.getId()).orElseThrow().getLeaseUntil())
                .isBefore(originalUntil);
    }

    @Test
    @DisplayName("멱등(본문 동일) 저장은 저장을 건너뛰므로 편집권을 해제하지 않는다")
    void identicalContentSaveKeepsLease() throws Exception {
        User owner = persistUser("owner-d18@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project, ProjectSectionStatus.DRAFTING);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistDraft(section, "그대로인 본문", 1, owner);
        persistActiveLease(section, owner);
        em.flush();

        save(section.getId(), owner, "그대로인 본문", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentVersion").value(1));

        em.flush();
        em.clear();
        // 편집권이 그대로 활성(미래 만료)으로 유지된다
        assertThat(draftLeaseRepository.findByProjectSection_Id(section.getId()).orElseThrow().getLeaseUntil())
                .isAfter(LocalDateTime.now(KST));
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

    /** 활성(유효) 편집권을 시드한다 — 저장 시 편집권 검사(§5.2.1)를 통과시키기 위함. */
    private DraftLease persistActiveLease(ProjectSection section, User holder) {
        return persistLease(section, holder, LocalDateTime.now(KST).plusHours(1));
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
