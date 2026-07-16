package com.wevo.backend.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.review.domain.ReviewSubmission;
import com.wevo.backend.review.domain.UnderstandingSignal;
import com.wevo.backend.review.repository.ReviewSubmissionRepository;
import com.wevo.backend.review.domain.ReviewLink;
import com.wevo.backend.review.repository.ReviewLinkRepository;
import com.wevo.backend.review.service.ReviewLinkService;
import com.wevo.backend.review.service.ReviewTokenHasher;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 검토 링크 전 경로(발급 → 열람 → 제출)를 실제 컨텍스트(H2)로 실행 검증한다.
 *
 * <p>프로젝트/섹션/멤버 생성 API가 아직 없어 EntityManager 로 직접 시드한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExternalReviewIntegrationTest {

    private static final String DRAFT_CONTENT = "우리가 해결하려는 문제는 정보가 흩어져 있다는 점이다.";
    private static final String REVIEWER_ID_HEADER = "X-Anonymous-Reviewer-Id";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ReviewSubmissionRepository reviewSubmissionRepository;
    @Autowired
    private ReviewLinkRepository reviewLinkRepository;
    @Autowired
    private ReviewLinkService reviewLinkService;
    @Autowired
    private ReviewTokenHasher tokenHasher;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("발급→열람→제출 전 경로가 정상 동작한다")
    void externalReviewFullFlow() throws Exception {
        User owner = persistUser("owner@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();

        // 1) 링크 발급 (팀장 인증)

        MvcResult issued = mockMvc.perform(post("/api/project-sections/{id}/review-links", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("REVIEW_LINK_CREATED"))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.reviewLinkId").isNumber())
                .andReturn();

        String token = objectMapper.readTree(issued.getResponse().getContentAsString())
                .path("data").path("token").asText();
        assertThat(token).isNotBlank();

        // 발급자(createdBy)가 팀장으로 세팅되는지 확인 (created_by_user_id 유실 방지)
        // 원문 토큰이 아니라 해시로만 저장되므로 해시로 조회한다.
        ReviewLink savedLink = reviewLinkRepository.findByTokenHash(tokenHasher.hash(token)).orElseThrow();
        assertThat(savedLink.getCreatedBy().getId()).isEqualTo(owner.getId());
        assertThat(savedLink.getTokenHash()).isNotEqualTo(token);
        assertThat(savedLink.getContentSnapshot()).isEqualTo(DRAFT_CONTENT);

        // 2) 공개 열람 (로그인 없이)
        mockMvc.perform(get("/public/review-links/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sectionId").value(section.getId()))
                .andExpect(jsonPath("$.data.sectionTitle").value("문제 정의"))
                .andExpect(jsonPath("$.data.content").value(DRAFT_CONTENT));

        // 3) 이해도 제출 (로그인 없이)
        mockMvc.perform(post("/public/review-links/{token}/submissions", token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "understandingSignal": "PARTIAL",
                                  "reviewerName": "외부검토자A",
                                  "summary": "3번째 문단이 이해하기 어려웠어요."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("REVIEW_SUBMITTED"))
                .andExpect(jsonPath("$.data.understandingSignal").value("PARTIAL"));

        // DB 반영 확인
        assertThat(reviewSubmissionRepository.count()).isEqualTo(1);
        ReviewSubmission saved = reviewSubmissionRepository.findAll().get(0);
        assertThat(saved.getUnderstandingSignal()).isEqualTo(UnderstandingSignal.PARTIAL);
        assertThat(saved.getReviewerName()).isEqualTo("외부검토자A");
        assertThat(saved.getSummary()).isEqualTo("3번째 문단이 이해하기 어려웠어요.");
    }

    @Test
    @DisplayName("팀원(MEMBER)이 링크를 발급하면 403 을 반환한다")
    void memberCannotIssueLink() throws Exception {
        User owner = persistUser("owner-m1@wevo.com", "팀장");
        Project project = persistProject(owner);
        ProjectSection section = persistSectionWithDraft(project, null);
        User member = persistUser("member@wevo.com", "팀원");
        persistMember(project, member, ProjectMemberRole.MEMBER);
        em.flush();

        mockMvc.perform(post("/api/project-sections/{id}/review-links", section.getId())
                        .with(authentication(authOf(member))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("링크는 발급 시점 본문 버전에 고정된다 — 본문이 수정돼도 스냅샷과 버전을 그대로 보여준다")
    void linkPinsIssuanceTimeDraftVersion() throws Exception {
        User owner = persistUser("owner-pin@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();

        // 발급 응답에 고정된 버전(v1)이 담긴다
        MvcResult issued = mockMvc.perform(post("/api/project-sections/{id}/review-links", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.contentVersion").value(1))
                .andReturn();
        String token = objectMapper.readTree(issued.getResponse().getContentAsString())
                .path("data").path("token").asText();

        // 본문 v2 저장 (+ 본문 저장 플로우가 호출하는 링크 만료 처리)
        persistDraft(section, "수정된 본문 v2", 2, owner);
        reviewLinkService.markSectionLinksOutdated(section.getId());

        // 열람은 여전히 발급 시점(v1) 스냅샷 — 링크 상태만 OUTDATED 로 안내
        mockMvc.perform(get("/public/review-links/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value(DRAFT_CONTENT))
                .andExpect(jsonPath("$.data.contentVersion").value(1))
                .andExpect(jsonPath("$.data.linkStatus").value("OUTDATED"));

        // 새 본문(v2)의 외부 검토는 재발급으로만 — 새 링크는 v2 에 고정된다
        mockMvc.perform(post("/api/project-sections/{id}/review-links", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.contentVersion").value(2));
    }

    @Test
    @DisplayName("본문 초안이 없는 섹션에 링크를 발급하면 409(R009) 를 반환한다")
    void cannotIssueLinkWithoutDraft() throws Exception {
        User owner = persistUser("owner-nodraft@wevo.com", "팀장");
        Project project = persistProject(owner);
        ProjectSection section = persistSectionWithoutDraft(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.flush();

        mockMvc.perform(post("/api/project-sections/{id}/review-links", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R009"));

        assertThat(reviewLinkRepository.count()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 토큰으로 열람하면 404(R001) 를 반환한다")
    void unknownTokenReturns404() throws Exception {
        mockMvc.perform(get("/public/review-links/{token}", "no-such-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    @Test
    @DisplayName("팀장이 외부 검토 결과(이해도 집계 + 개별 코멘트)를 조회한다")
    void ownerViewsExternalReviewResults() throws Exception {
        User owner = persistUser("owner2@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();

        String token = issueLink(section.getId(), owner);
        submit(token, "PARTIAL", "외부검토자A", "3번째 문단이 애매합니다.");
        submit(token, "CLEAR", "외부검토자B", "전반적으로 이해됩니다.");

        mockMvc.perform(get("/api/project-sections/{id}/review-submissions", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.clearCount").value(1))
                .andExpect(jsonPath("$.data.partialCount").value(1))
                .andExpect(jsonPath("$.data.unclearCount").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].understandingSignal").exists())
                .andExpect(jsonPath("$.data.items[0].reviewerName").exists())
                .andExpect(jsonPath("$.data.items[0].summary").exists());
    }

    @Test
    @DisplayName("재발급 후 결과 조회는 버전별 집계(byVersion)로 어느 본문에 대한 평가인지 구분한다")
    void resultsAreAggregatedPerContentVersion() throws Exception {
        User owner = persistUser("owner-ver@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();

        // v1 링크에 2건 제출 (CLEAR 1, PARTIAL 1)
        String tokenV1 = issueLink(section.getId(), owner);
        submitAsReviewer(tokenV1, "CLEAR", "browser-v1-a").andExpect(status().isCreated());
        submitAsReviewer(tokenV1, "PARTIAL", "browser-v1-b").andExpect(status().isCreated());

        // 본문 v2 저장(+링크 만료) 후 재발급, v2 링크에 1건 제출 (CLEAR)
        persistDraft(section, "수정된 본문 v2", 2, owner);
        reviewLinkService.markSectionLinksOutdated(section.getId());
        String tokenV2 = issueLink(section.getId(), owner);
        submitAsReviewer(tokenV2, "CLEAR", "browser-v2-a").andExpect(status().isCreated());

        mockMvc.perform(get("/api/project-sections/{id}/review-submissions", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                // 전체 합산은 유지된다
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.clearCount").value(2))
                .andExpect(jsonPath("$.data.partialCount").value(1))
                // 버전별 집계 — 최신 버전(v2)이 먼저
                .andExpect(jsonPath("$.data.byVersion.length()").value(2))
                .andExpect(jsonPath("$.data.byVersion[0].contentVersion").value(2))
                .andExpect(jsonPath("$.data.byVersion[0].totalCount").value(1))
                .andExpect(jsonPath("$.data.byVersion[0].clearCount").value(1))
                .andExpect(jsonPath("$.data.byVersion[1].contentVersion").value(1))
                .andExpect(jsonPath("$.data.byVersion[1].totalCount").value(2))
                .andExpect(jsonPath("$.data.byVersion[1].clearCount").value(1))
                .andExpect(jsonPath("$.data.byVersion[1].partialCount").value(1))
                // 개별 항목에도 검토한 버전이 표기된다 (최신순 — 첫 항목이 v2 제출)
                .andExpect(jsonPath("$.data.items[0].contentVersion").value(2));
    }

    @Test
    @DisplayName("팀원(MEMBER)이 외부 검토 결과를 조회하면 403 을 반환한다")
    void memberCannotViewResults() throws Exception {
        User owner = persistUser("owner-m2@wevo.com", "팀장");
        Project project = persistProject(owner);
        ProjectSection section = persistSectionWithDraft(project, null);
        User member = persistUser("member2@wevo.com", "팀원");
        persistMember(project, member, ProjectMemberRole.MEMBER);
        em.flush();

        mockMvc.perform(get("/api/project-sections/{id}/review-submissions", section.getId())
                        .with(authentication(authOf(member))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("같은 브라우저(익명 키)로 두 번 제출하면 두 번째는 409(R002) 를 반환한다")
    void sameBrowserCannotSubmitTwice() throws Exception {
        User owner = persistUser("owner-dup@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String token = issueLink(section.getId(), owner);

        submitAsReviewer(token, "CLEAR", "browser-A").andExpect(status().isCreated());
        submitAsReviewer(token, "PARTIAL", "browser-A")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R002"));

        assertThat(reviewSubmissionRepository.countByReviewLink_Id(linkId(token))).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 브라우저는 각각 1회씩 제출할 수 있다")
    void differentBrowsersEachSubmitOnce() throws Exception {
        User owner = persistUser("owner-multi@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String token = issueLink(section.getId(), owner);

        submitAsReviewer(token, "CLEAR", "browser-A").andExpect(status().isCreated());
        submitAsReviewer(token, "UNCLEAR", "browser-B").andExpect(status().isCreated());

        assertThat(reviewSubmissionRepository.countByReviewLink_Id(linkId(token))).isEqualTo(2);
    }

    @Test
    @DisplayName("링크당 21번째 제출은 409(R003) 로 상한을 넘지 못한다")
    void submissionCapIsEnforced() throws Exception {
        User owner = persistUser("owner-cap@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String token = issueLink(section.getId(), owner);

        for (int i = 0; i < ReviewLink.MAX_SUBMISSIONS; i++) {
            submitAsReviewer(token, "CLEAR", "browser-" + i).andExpect(status().isCreated());
        }
        submitAsReviewer(token, "CLEAR", "browser-over")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R003"));

        assertThat(reviewSubmissionRepository.countByReviewLink_Id(linkId(token)))
                .isEqualTo(ReviewLink.MAX_SUBMISSIONS);
    }

    @Test
    @DisplayName("본문 수정으로 만료(OUTDATED)된 링크에 제출하면 409(R004) 를 반환한다")
    void outdatedLinkRejectsSubmission() throws Exception {
        User owner = persistUser("owner-outdated@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String token = issueLink(section.getId(), owner);

        // 본문 저장 시점에 호출되는 만료 처리
        reviewLinkService.markSectionLinksOutdated(section.getId());

        submitAsReviewer(token, "CLEAR", "browser-A")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R004"));
    }

    @Test
    @DisplayName("팀장이 비활성화(CLOSED)한 링크에 제출하면 409(R005) 를 반환한다")
    void closedLinkRejectsSubmission() throws Exception {
        User owner = persistUser("owner-closed@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String token = issueLink(section.getId(), owner);
        Long linkId = linkId(token);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/review-links/{id}", linkId)
                        .with(authentication(authOf(owner)))
                        .contentType("application/json")
                        .content("{ \"status\": \"CLOSED\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REVIEW_LINK_CLOSED"));

        submitAsReviewer(token, "CLEAR", "browser-A")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R005"));
    }

    @Test
    @DisplayName("이미 제출한 브라우저가 다시 열람하면 alreadySubmitted=true 로 안내한다")
    void alreadySubmittedFlagIsExposedOnView() throws Exception {
        User owner = persistUser("owner-flag@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String token = issueLink(section.getId(), owner);

        submitAsReviewer(token, "CLEAR", "browser-A").andExpect(status().isCreated());

        mockMvc.perform(get("/public/review-links/{token}", token)
                        .header(REVIEWER_ID_HEADER, "browser-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadySubmitted").value(true))
                .andExpect(jsonPath("$.data.content").value(DRAFT_CONTENT));

        mockMvc.perform(get("/public/review-links/{token}", token)
                        .header(REVIEWER_ID_HEADER, "browser-B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadySubmitted").value(false));
    }

    @Test
    @DisplayName("summary 는 CLEAR·PARTIAL 이면 필수(400 C001), UNCLEAR 이면 선택(201)")
    void summaryRequiredExceptForUnclear() throws Exception {
        User owner = persistUser("owner-summary@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String token = issueLink(section.getId(), owner);

        // summary 없이 CLEAR → 400 (교차 검증 실패)
        submitWithoutSummary(token, "CLEAR", "browser-clear")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors").isNotEmpty());

        // 공백만 있는 summary 도 CLEAR 에서는 400
        submitWithBlankSummary(token, "CLEAR", "browser-blank")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        // summary 없이 PARTIAL → 400
        submitWithoutSummary(token, "PARTIAL", "browser-partial")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        // summary 없이 UNCLEAR → 201 (선택)
        submitWithoutSummary(token, "UNCLEAR", "browser-unclear")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.understandingSignal").value("UNCLEAR"));
    }

    // --- 시드 헬퍼 ---

    private org.springframework.test.web.servlet.ResultActions submitWithoutSummary(
            String token, String signal, String reviewerId) throws Exception {
        return mockMvc.perform(post("/public/review-links/{token}/submissions", token)
                .header(REVIEWER_ID_HEADER, reviewerId)
                .contentType("application/json")
                .content("{ \"understandingSignal\": \"%s\" }".formatted(signal)));
    }

    private org.springframework.test.web.servlet.ResultActions submitWithBlankSummary(
            String token, String signal, String reviewerId) throws Exception {
        return mockMvc.perform(post("/public/review-links/{token}/submissions", token)
                .header(REVIEWER_ID_HEADER, reviewerId)
                .contentType("application/json")
                .content("{ \"understandingSignal\": \"%s\", \"summary\": \"   \" }".formatted(signal)));
    }

    private org.springframework.test.web.servlet.ResultActions submitAsReviewer(
            String token, String signal, String reviewerId) throws Exception {
        // CLEAR·PARTIAL 은 summary 가 필수이므로 항상 채워 보낸다. (UNCLEAR 에는 무해)
        return mockMvc.perform(post("/public/review-links/{token}/submissions", token)
                .header(REVIEWER_ID_HEADER, reviewerId)
                .contentType("application/json")
                .content("{ \"understandingSignal\": \"%s\", \"summary\": \"핵심을 이해했어요.\" }".formatted(signal)));
    }

    private Long linkId(String token) {
        return reviewLinkRepository.findByTokenHash(tokenHasher.hash(token)).orElseThrow().getId();
    }

    private UsernamePasswordAuthenticationToken authOf(User user) {
        return new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(user.getId()), null, AuthorityUtils.NO_AUTHORITIES);
    }

    private String issueLink(Long sectionId, User owner) throws Exception {
        MvcResult issued = mockMvc.perform(post("/api/project-sections/{id}/review-links", sectionId)
                        .with(authentication(authOf(owner))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(issued.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    private void submit(String token, String signal, String reviewerName, String summary) throws Exception {
        mockMvc.perform(post("/public/review-links/{token}/submissions", token)
                        .contentType("application/json")
                        .content("""
                                { "understandingSignal": "%s", "reviewerName": "%s", "summary": "%s" }
                                """.formatted(signal, reviewerName, summary)))
                .andExpect(status().isCreated());
    }

    private User persistUser(String email, String name) {
        User user = User.builder().name(name).email(email).status(UserStatus.ACTIVE).build();
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

    private ProjectSection persistSectionWithoutDraft(Project project) {
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .title("문제 정의")
                .sectionOrder(1)
                .status(ProjectSectionStatus.REVIEWING)
                .needsReReview(false)
                .build();
        em.persist(section);
        return section;
    }

    private ProjectSection persistSectionWithDraft(Project project, User editor) {
        ProjectSection section = persistSectionWithoutDraft(project);
        persistDraft(section, DRAFT_CONTENT, 1, editor);
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
        em.flush();
    }
}
