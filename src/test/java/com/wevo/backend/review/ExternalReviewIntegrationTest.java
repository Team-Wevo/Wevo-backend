package com.wevo.backend.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.review.domain.ReviewLinkStatus;
import com.wevo.backend.review.domain.ReviewSubmission;
import com.wevo.backend.review.domain.ReviewIntentComparison;
import com.wevo.backend.review.domain.ReviewIntentComparisonStatus;
import com.wevo.backend.review.domain.UnderstandingSignal;
import com.wevo.backend.review.repository.ReviewSubmissionRepository;
import com.wevo.backend.review.domain.ReviewLink;
import com.wevo.backend.review.repository.ReviewLinkRepository;
import com.wevo.backend.review.repository.ReviewIntentComparisonRepository;
import com.wevo.backend.review.service.ReviewLinkService;
import com.wevo.backend.global.security.TokenHasher;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionAuthorIntent;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 검토 링크 전 경로(발급 → 열람 → 제출)를 실제 PostgreSQL 컨텍스트로 실행 검증한다.
 *
 * <p>프로젝트/섹션/멤버 생성 API가 아직 없어 EntityManager 로 직접 시드한다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.review.abuse.rate-limit.per-link-per-minute=100",
        "app.review.abuse.rate-limit.per-link-per-hour=100"
})
@Import(PostgresTestContainerConfig.class)
@AutoConfigureMockMvc
@Transactional
class ExternalReviewIntegrationTest {

    private static final String DRAFT_CONTENT = "우리가 해결하려는 문제는 정보가 흩어져 있다는 점이다.";
    private static final String REVIEWER_ID_HEADER = "X-Anonymous-Reviewer-Id";
    /** 유효 기간 판정 기준 시간대 — 서비스와 같은 KST 로 맞춘다. (CLAUDE.md §5.4) */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 익명 검토자 키는 UUID v4 형식이어야 한다 — 테스트 가독성을 위해 라벨("browser-A")을 쓰되,
     * 같은 라벨은 같은 키로 매핑해 "같은 브라우저" 시나리오를 표현한다.
     */
    private final java.util.Map<String, String> reviewerIds = new java.util.HashMap<>();

    private String reviewerId(String label) {
        return reviewerIds.computeIfAbsent(label, key -> java.util.UUID.randomUUID().toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ReviewSubmissionRepository reviewSubmissionRepository;
    @Autowired
    private ReviewLinkRepository reviewLinkRepository;
    @Autowired
    private ReviewLinkService reviewLinkService;
    @Autowired
    private ReviewIntentComparisonRepository comparisonRepository;
    @Autowired
    private TokenHasher tokenHasher;

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
        assertThat(savedLink.getAuthorIntentSnapshot())
                .isEqualTo("정보가 흩어진 문제를 해결하는 것이 핵심이다.");
        assertThat(savedLink.getAuthorIntent()).isNotNull();

        // 2) 공개 열람 (로그인 없이)
        mockMvc.perform(get("/public/review-links/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sectionId").value(section.getId()))
                .andExpect(jsonPath("$.data.sectionTitle").value("문제 정의"))
                .andExpect(jsonPath("$.data.content").value(DRAFT_CONTENT))
                .andExpect(jsonPath("$.data.authorIntent").doesNotExist());

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

        // 만료 전 열람은 발급 시점(v1) 스냅샷을 보여준다
        mockMvc.perform(get("/public/review-links/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value(DRAFT_CONTENT))
                .andExpect(jsonPath("$.data.contentVersion").value(1))
                .andExpect(jsonPath("$.data.linkStatus").value("ACTIVE"));

        // 본문 v2 저장 (+ 본문 저장 플로우가 호출하는 링크 만료 처리)
        persistDraft(section, "수정된 본문 v2", 2, owner);
        reviewLinkService.markSectionLinksOutdated(section.getId());

        // 만료된 뒤에는 스냅샷도 보여주지 않는다 — 열람부터 R004 로 거절
        mockMvc.perform(get("/public/review-links/{token}", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R004"))
                .andExpect(jsonPath("$.data").doesNotExist());

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
    @DisplayName("확정 작성자 의도가 없는 최신 초안은 링크 발급을 409(R010)으로 거부한다")
    void cannotIssueLinkWithoutConfirmedAuthorIntent() throws Exception {
        User owner = persistUser("owner-nointent@wevo.com", "팀장");
        Project project = persistProject(owner);
        ProjectSection section = persistSectionWithoutDraft(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.persist(SectionDraft.builder()
                .projectSection(section)
                .content(DRAFT_CONTENT)
                .version(1)
                .lastEditor(owner)
                .build());
        em.flush();

        mockMvc.perform(post("/api/project-sections/{id}/review-links", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R010"));
    }

    @Test
    @DisplayName("같은 본문 version의 확정 의도를 바꾸면 기존 ACTIVE 링크가 OUTDATED 된다")
    void changingConfirmedIntentInvalidatesActiveLink() throws Exception {
        User owner = persistUser("owner-intent-change@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String token = issueLink(section.getId(), owner);

        mockMvc.perform(put("/api/project-sections/{id}/author-intent", section.getId())
                        .with(authentication(authOf(owner)))
                        .contentType("application/json")
                        .content("""
                                {
                                  "contentVersion": 1,
                                  "intent": "정보를 하나의 실행 가능한 제안으로 정리하는 것이 핵심이다."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTHOR_INTENT_CONFIRMED"));

        ReviewLink savedLink = reviewLinkRepository
                .findByTokenHash(tokenHasher.hash(token))
                .orElseThrow();
        assertThat(savedLink.getStatus()).isEqualTo(ReviewLinkStatus.OUTDATED);
        assertThat(savedLink.getAuthorIntentSnapshot())
                .isEqualTo("정보가 흩어진 문제를 해결하는 것이 핵심이다.");
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
                .andExpect(jsonPath("$.data.items[0].summary").exists())
                .andExpect(jsonPath("$.data.items[0].authorIntent")
                        .value("정보가 흩어진 문제를 해결하는 것이 핵심이다."))
                .andExpect(jsonPath("$.data.items[0].comparison.status").value("PENDING"));
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
    @DisplayName("팀장이 수동 종료(CLOSED)한 링크에 제출하면 409(R005) 를 반환한다")
    void closedLinkRejectsSubmission() throws Exception {
        User owner = persistUser("owner-closed@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String token = issueLink(section.getId(), owner);
        Long linkId = linkId(token);

        closeLink(linkId, owner, "CLOSED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REVIEW_LINK_CLOSED"));
        assertThat(statusOf(linkId)).isEqualTo(ReviewLinkStatus.CLOSED);

        // 열람도 같은 사유로 막힌다 — 본문을 보여준 뒤 제출만 거절하지 않는다.
        mockMvc.perform(get("/public/review-links/{token}", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R005"));

        submitAsReviewer(token, "CLEAR", "browser-A")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R005"));
    }

    @Test
    @DisplayName("이미 종료된 링크를 다시 종료하면 409(R011) 로 거절하고 상태를 유지한다")
    void closingAlreadyClosedLinkIsRejected() throws Exception {
        User owner = persistUser("owner-idem@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        Long linkId = linkId(issueLink(section.getId(), owner));

        closeLink(linkId, owner, "CLOSED").andExpect(status().isOk());
        // 종료는 ACTIVE 링크에서만 성공한다 — 두 번째 호출은 사유를 구분해 거절한다.
        closeLink(linkId, owner, "CLOSED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("R011"))
                .andExpect(jsonPath("$.message").value("이미 종료된 링크입니다."));

        assertThat(statusOf(linkId)).isEqualTo(ReviewLinkStatus.CLOSED);
    }

    @Test
    @DisplayName("본문 수정으로 만료(OUTDATED)된 링크를 종료하면 409(R012) 로 거절한다")
    void closingOutdatedLinkIsRejected() throws Exception {
        User owner = persistUser("owner-keep-outdated@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String token = issueLink(section.getId(), owner);
        Long linkId = linkId(token);

        // 본문 저장 시점에 호출되는 만료 처리
        reviewLinkService.markSectionLinksOutdated(section.getId());

        // 이미 만료돼 제출을 받지 않는 링크를 닫는 건 성립하지 않는 요청이라 거절한다
        closeLink(linkId, owner, "CLOSED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R012"))
                .andExpect(jsonPath("$.message").value("이미 만료된 링크입니다."));
        assertThat(statusOf(linkId)).isEqualTo(ReviewLinkStatus.OUTDATED);

        // 외부 검토자에게 안내되는 사유도 그대로 "본문이 수정돼 만료"(R004)
        submitAsReviewer(token, "CLEAR", "browser-A")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R004"));
    }

    @Test
    @DisplayName("CLOSED 외의 상태로 변경 요청하면 400(C001) 을 반환한다")
    void onlyClosedTransitionIsAllowed() throws Exception {
        User owner = persistUser("owner-transition@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        Long linkId = linkId(issueLink(section.getId(), owner));

        closeLink(linkId, owner, "OUTDATED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        assertThat(statusOf(linkId)).isEqualTo(ReviewLinkStatus.ACTIVE);
    }

    @Test
    @DisplayName("팀원(MEMBER)이 링크를 종료하면 403(A002) — 링크 관리 권한은 OWNER 전용")
    void memberCannotCloseLink() throws Exception {
        User owner = persistUser("owner-close-member@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        User member = persistUser("member-close@wevo.com", "팀원");
        persistMember(section.getProject(), member, ProjectMemberRole.MEMBER);
        em.flush();
        Long linkId = linkId(issueLink(section.getId(), owner));

        closeLink(linkId, member, "CLOSED")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));

        assertThat(statusOf(linkId)).isEqualTo(ReviewLinkStatus.ACTIVE);
    }

    @Test
    @DisplayName("비멤버가 링크를 종료하면 존재하지 않는 링크와 같은 404(R001) 로 링크 존재를 숨긴다")
    void nonMemberCannotCloseLink() throws Exception {
        User owner = persistUser("owner-close-stranger@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        User stranger = persistUser("stranger-close@wevo.com", "외부인");
        em.flush();
        Long linkId = linkId(issueLink(section.getId(), owner));

        // 남의 링크와 없는 링크의 응답이 완전히 같아야 ID 를 훑어 실재 여부를 알아낼 수 없다.
        closeLink(linkId, stranger, "CLOSED")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
        closeLink(999_999L, stranger, "CLOSED")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));

        assertThat(statusOf(linkId)).isEqualTo(ReviewLinkStatus.ACTIVE);
    }

    @Test
    @DisplayName("존재하지 않는 링크를 종료하면 404(R001) 을 반환한다")
    void closingUnknownLinkReturnsNotFound() throws Exception {
        User owner = persistUser("owner-close-unknown@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();

        closeLink(999_999L, owner, "CLOSED")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    @Test
    @DisplayName("현재 활성 링크 조회는 토큰 없이 메타데이터를 복구하고, 제출 수는 링크당으로 센다")
    void currentActiveLinkIsRecoverableWithoutToken() throws Exception {
        User owner = persistUser("owner-current@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String token = issueLink(section.getId(), owner);
        Long linkId = linkId(token);

        submitAsReviewer(token, "CLEAR", "browser-A").andExpect(status().isCreated());

        mockMvc.perform(get("/api/project-sections/{id}/review-links/current", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.reviewLinkId").value(linkId))
                .andExpect(jsonPath("$.data.linkStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.contentVersion").value(1))
                .andExpect(jsonPath("$.data.submissionCount").value(1))
                .andExpect(jsonPath("$.data.issuedAt").exists())
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    @Test
    @DisplayName("활성 링크가 없으면 현재 링크 조회는 200 + data 생략으로 응답한다")
    void currentReturnsOkWithoutDataWhenNoActiveLink() throws Exception {
        User owner = persistUser("owner-nocurrent@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();

        // 발급한 적 없음 → 활성 링크 없음 (조회 자체는 성공이므로 200)
        mockMvc.perform(get("/api/project-sections/{id}/review-links/current", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 발급 후 수동 종료 → 다시 활성 링크 없음
        String token = issueLink(section.getId(), owner);
        closeLink(linkId(token), owner, "CLOSED").andExpect(status().isOk());

        mockMvc.perform(get("/api/project-sections/{id}/review-links/current", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("팀원(MEMBER)이 현재 링크를 조회하면 403(A002) — 링크 관리 권한은 OWNER 전용")
    void currentLinkForbiddenForMember() throws Exception {
        User owner = persistUser("owner-cur-member@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        User member = persistUser("member-cur@wevo.com", "팀원");
        persistMember(section.getProject(), member, ProjectMemberRole.MEMBER);
        em.flush();

        mockMvc.perform(get("/api/project-sections/{id}/review-links/current", section.getId())
                        .with(authentication(authOf(member))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    @Test
    @DisplayName("비멤버가 현재 링크를 조회하면 404(S001) 로 존재를 숨긴다")
    void currentLinkHiddenForNonMember() throws Exception {
        User owner = persistUser("owner-cur-nonmember@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        User stranger = persistUser("stranger-cur@wevo.com", "외부인");
        em.flush();

        mockMvc.perform(get("/api/project-sections/{id}/review-links/current", section.getId())
                        .with(authentication(authOf(stranger))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S001"));
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
                        .header(REVIEWER_ID_HEADER, reviewerId("browser-A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadySubmitted").value(true))
                .andExpect(jsonPath("$.data.content").value(DRAFT_CONTENT));

        mockMvc.perform(get("/public/review-links/{token}", token)
                        .header(REVIEWER_ID_HEADER, reviewerId("browser-B")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadySubmitted").value(false));
    }

    @Test
    @DisplayName("이해됨·애매함은 summary 없이 제출하면 400(C001) 로 거절한다 (정책서 §6.2.3)")
    void summaryIsRequiredForUnderstoodSignals() throws Exception {
        User owner = persistUser("owner-summary@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String token = issueLink(section.getId(), owner);

        submitWithoutSummary(token, "CLEAR", "browser-clear")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        submitWithoutSummary(token, "PARTIAL", "browser-partial")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        // 공백만 보낸 우회도 같은 이유로 막는다.
        submitWithSummary(token, "CLEAR", "browser-blank", "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        assertThat(reviewSubmissionRepository.count()).isZero();
    }

    @Test
    @DisplayName("추가 코멘트는 선택 입력이라 없으면 null 로 저장되고, 넣으면 결과 조회에 노출된다")
    void reviewerCommentIsOptionalAndExposedToOwner() throws Exception {
        User owner = persistUser("owner-comment@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String token = issueLink(section.getId(), owner);

        // 코멘트 미입력 — summary 만 있으면 제출은 정상 처리된다.
        submitAsReviewer(token, "CLEAR", "browser-no-comment")
                .andExpect(status().isCreated());
        // 코멘트 입력
        mockMvc.perform(post("/public/review-links/{token}/submissions", token)
                        .header(REVIEWER_ID_HEADER, reviewerId("browser-with-comment"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "understandingSignal": "PARTIAL",
                                  "summary": "핵심을 이해했어요.",
                                  "reviewerComment": "2문단이 길어요."
                                }
                                """))
                .andExpect(status().isCreated());

        em.flush();
        em.clear();
        assertThat(reviewSubmissionRepository.findAll())
                .extracting(ReviewSubmission::getReviewerComment)
                .containsExactlyInAnyOrder(null, "2문단이 길어요.");

        mockMvc.perform(get("/api/project-sections/{id}/review-submissions", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.understandingSignal == 'PARTIAL')].reviewerComment")
                        .value("2문단이 길어요."))
                // 미입력 코멘트는 null 로 싣지 않고 키를 생략한다 (CLAUDE.md §5.4).
                .andExpect(jsonPath("$.data.items[?(@.understandingSignal == 'CLEAR')].reviewerComment")
                        .isEmpty());
    }

    @Test
    @DisplayName("이해 어려움은 summary 없이 제출할 수 있고 비교는 NOT_AVAILABLE 로 남는다")
    void summaryStaysOptionalForUnclearSignal() throws Exception {
        User owner = persistUser("owner-unclear-summary@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String token = issueLink(section.getId(), owner);

        // "이해하지 못했다"는 답 자체가 신호라 문장을 강제하지 않는다. 대신 대조할 이해가 없으므로
        // 의도 vs 이해 비교(REV-04)는 성립하지 않는다.
        submitWithoutSummary(token, "UNCLEAR", "browser-unclear")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.understandingSignal").value("UNCLEAR"));

        assertThat(comparisonRepository.findAll())
                .hasSize(1)
                .allMatch(comparison -> comparison.getStatus()
                        == ReviewIntentComparisonStatus.NOT_AVAILABLE);
    }

    @Test
    @DisplayName("의도와 summary가 있는 제출은 공개 응답을 기다리게 하지 않고 PENDING 비교를 저장한다")
    void comparableSubmissionIsSavedAsPendingWithoutPublicAiDetails() throws Exception {
        User owner = persistUser("owner-pending@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String token = issueLink(section.getId(), owner);

        submitAsReviewer(token, "PARTIAL", "browser-pending")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requestId").doesNotExist())
                .andExpect(jsonPath("$.data.authorIntent").doesNotExist())
                .andExpect(jsonPath("$.data.comparison").doesNotExist());

        ReviewIntentComparison comparison = comparisonRepository.findAll().get(0);
        assertThat(comparison.getStatus()).isEqualTo(ReviewIntentComparisonStatus.PENDING);
        assertThat(comparison.getIntentSnapshotHash()).matches("[0-9a-f]{64}");
        assertThat(comparison.getReviewerSummaryHash()).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("동일 section-version 의도는 DB 유니크 제약으로 중복을 막는다")
    void authorIntentUniqueConstraintIsEnforced() {
        User owner = persistUser("owner-unique-intent@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();

        assertThatThrownBy(() -> {
            em.persist(SectionAuthorIntent.confirmed(
                    section, 1, "중복 의도다.", owner, LocalDateTime.now()));
            em.flush();
        })
                .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }

    @Test
    @DisplayName("동일 submission 비교는 DB 유니크 제약으로 중복을 막는다")
    void comparisonUniqueConstraintIsEnforced() throws Exception {
        User owner = persistUser("owner-unique-comparison@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String token = issueLink(section.getId(), owner);
        submitAsReviewer(token, "PARTIAL", "browser-unique-comparison")
                .andExpect(status().isCreated());

        ReviewIntentComparison saved = comparisonRepository.findAll().get(0);
        assertThatThrownBy(() -> {
            em.persist(ReviewIntentComparison.pending(
                    saved.getReviewSubmission(),
                    saved.getIntentSnapshotHash(),
                    saved.getReviewerSummaryHash(),
                    saved.getPromptVersion(),
                    saved.getSchemaVersion(),
                    saved.getModelId()));
            em.flush();
        })
                .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }

    @Test
    @DisplayName("익명 검토자 키가 UUID v4 형식이 아니면 400(C001) 을 반환한다 (API_SPEC §3.5)")
    void invalidReviewerIdFormatIsRejected() throws Exception {
        User owner = persistUser("owner-uuid@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String token = issueLink(section.getId(), owner);

        mockMvc.perform(post("/public/review-links/{token}/submissions", token)
                        .header(REVIEWER_ID_HEADER, "not-a-uuid")
                        .contentType("application/json")
                        // 본문은 유효하게 둬서 검토자 키 형식만으로 거절되는지 본다.
                        .content("{ \"understandingSignal\": \"CLEAR\", \"summary\": \"핵심을 이해했어요.\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        assertThat(reviewSubmissionRepository.count()).isZero();
    }

    @Test
    @DisplayName("재발급하면 기존 ACTIVE 링크가 CLOSED 로 닫힌다 — 섹션당 ACTIVE 1개 (대체 발급)")
    void reissueClosesPreviousActiveLink() throws Exception {
        User owner = persistUser("owner-replace@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();

        String firstToken = issueLink(section.getId(), owner);
        String secondToken = issueLink(section.getId(), owner);

        // 기존 링크는 CLOSED — 제출이 거부된다 (기존 제출 결과는 보존)
        submitAsReviewer(firstToken, "CLEAR", "browser-old")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R005"));

        // 새 링크만 ACTIVE — 정상 제출된다
        submitAsReviewer(secondToken, "CLEAR", "browser-new").andExpect(status().isCreated());

        assertThat(reviewLinkRepository
                .findByProjectSection_IdAndStatus(section.getId(), com.wevo.backend.review.domain.ReviewLinkStatus.ACTIVE))
                .hasSize(1);
    }

    @Test
    @DisplayName("유효 기간을 지정해 발급하면 발급·복구 조회 응답에 만료일이 실린다")
    void issuedLinkCarriesExpiryDate() throws Exception {
        User owner = persistUser("owner-expiry@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();

        LocalDate expiresOn = LocalDate.now(KST).plusDays(7);
        mockMvc.perform(post("/api/project-sections/{id}/review-links", section.getId())
                        .with(authentication(authOf(owner)))
                        .contentType("application/json")
                        .content("{ \"expiresOn\": \"%s\" }".formatted(expiresOn)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.expiresOn").value(expiresOn.toString()));

        mockMvc.perform(get("/api/project-sections/{id}/review-links/current", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.expiresOn").value(expiresOn.toString()));
    }

    @Test
    @DisplayName("유효 기간을 생략하면 기간 제한 없이 발급되고 응답에서 만료일 키가 생략된다")
    void expiryIsOptional() throws Exception {
        User owner = persistUser("owner-noexpiry@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();

        // 본문 없이 호출하는 기존 클라이언트도 그대로 동작한다.
        mockMvc.perform(post("/api/project-sections/{id}/review-links", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.expiresOn").doesNotExist());

        // 명시적 null 과 빈 문자열도 생략과 똑같이 "기간 제한 없음"으로 읽는다 — 입력칸을 비운 채
        // 보내는 폼 제출을 400 으로 튕기지 않기 위함이다. (재발급이라 이전 링크는 닫힌다)
        for (String blank : java.util.List.of("null", "\"\"")) {
            mockMvc.perform(post("/api/project-sections/{id}/review-links", section.getId())
                            .with(authentication(authOf(owner)))
                            .contentType("application/json")
                            .content("{ \"expiresOn\": %s }".formatted(blank)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.expiresOn").doesNotExist());
        }

        mockMvc.perform(get("/api/project-sections/{id}/review-links/current", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.expiresOn").doesNotExist());
    }

    @Test
    @DisplayName("유효 기간이 발급일보다 1일 이상 뒤가 아니면 422(C002) 로 거부한다")
    void expiryMustBeAtLeastOneDayAhead() throws Exception {
        User owner = persistUser("owner-expiry-today@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();

        // 하루도 열려 있지 않은 링크는 발급 자체가 성립하지 않는다 — 당일도, 과거도 거부.
        for (LocalDate invalid : java.util.List.of(LocalDate.now(KST), LocalDate.now(KST).minusDays(1))) {
            mockMvc.perform(post("/api/project-sections/{id}/review-links", section.getId())
                            .with(authentication(authOf(owner)))
                            .contentType("application/json")
                            .content("{ \"expiresOn\": \"%s\" }".formatted(invalid)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("C002"))
                    .andExpect(jsonPath("$.errors[0].field").value("expiresOn"));
        }

        assertThat(reviewLinkRepository.count()).isZero();
    }

    @Test
    @DisplayName("유효 기간이 지난 링크는 열람에 EXPIRED 로 안내하고 제출을 409(R013) 로 거부한다")
    void pastDueLinkRejectsSubmission() throws Exception {
        User owner = persistUser("owner-pastdue@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String token = issueLinkExpiringOn(section.getId(), owner, LocalDate.now(KST).plusDays(1));
        Long linkId = linkId(token);

        // 기간 안에는 정상 제출된다
        submitAsReviewer(token, "CLEAR", "browser-in-time").andExpect(status().isCreated());

        // 기간이 지난 뒤 — 저장된 상태는 아직 ACTIVE 지만 날짜로 판정해 거부한다 (배치 없음)
        expireBy(linkId, LocalDate.now(KST).minusDays(1));
        assertThat(statusOf(linkId)).isEqualTo(ReviewLinkStatus.ACTIVE);

        // 열람부터 막힌다 — 스냅샷 본문을 보여주지 않는다
        mockMvc.perform(get("/public/review-links/{token}", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R013"))
                .andExpect(jsonPath("$.data").doesNotExist());

        submitAsReviewer(token, "CLEAR", "browser-too-late")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R013"))
                .andExpect(jsonPath("$.message").value("외부 검토 링크의 유효 기간이 지났어요."));

        // 기간 안에 들어온 제출 결과는 보존된다
        assertThat(reviewSubmissionRepository.countByReviewLink_Id(linkId)).isEqualTo(1);
    }

    @Test
    @DisplayName("유효 기간이 지난 링크는 현재 활성 링크 조회에서 빠진다 — 팀장이 재발급으로 넘어가게")
    void pastDueLinkIsNotReturnedAsCurrent() throws Exception {
        User owner = persistUser("owner-pastdue-current@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        Long linkId = linkId(issueLinkExpiringOn(
                section.getId(), owner, LocalDate.now(KST).plusDays(1)));

        expireBy(linkId, LocalDate.now(KST).minusDays(1));

        mockMvc.perform(get("/api/project-sections/{id}/review-links/current", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("재발급하면 기간이 지난 기존 링크는 CLOSED 가 아니라 EXPIRED 로 정리된다")
    void reissueMarksPastDueLinkExpired() throws Exception {
        User owner = persistUser("owner-pastdue-reissue@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String oldToken = issueLinkExpiringOn(section.getId(), owner, LocalDate.now(KST).plusDays(1));
        Long oldLinkId = linkId(oldToken);

        expireBy(oldLinkId, LocalDate.now(KST).minusDays(1));
        issueLink(section.getId(), owner);

        // 종결 사유가 남아야 팀장 화면이 "종료했다"가 아니라 "기간이 지났다"로 안내할 수 있다.
        assertThat(statusOf(oldLinkId)).isEqualTo(ReviewLinkStatus.EXPIRED);
        submitAsReviewer(oldToken, "CLEAR", "browser-old-expired")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R013"));
    }

    @Test
    @DisplayName("본문 수정 시 기간이 먼저 지난 링크는 OUTDATED 로 덮이지 않고 EXPIRED 로 남는다")
    void contentEditDoesNotOverwritePastDueReason() throws Exception {
        User owner = persistUser("owner-pastdue-outdated@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        Long linkId = linkId(issueLinkExpiringOn(
                section.getId(), owner, LocalDate.now(KST).plusDays(1)));

        expireBy(linkId, LocalDate.now(KST).minusDays(1));
        reviewLinkService.markSectionLinksOutdated(section.getId());

        assertThat(statusOf(linkId)).isEqualTo(ReviewLinkStatus.EXPIRED);
    }

    @Test
    @DisplayName("유효 기간이 지난 링크를 수동 종료하면 409(R014) 로 거절하고 상태를 바꾸지 않는다")
    void closingPastDueLinkIsRejected() throws Exception {
        User owner = persistUser("owner-pastdue-close@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        Long linkId = linkId(issueLinkExpiringOn(
                section.getId(), owner, LocalDate.now(KST).plusDays(1)));

        expireBy(linkId, LocalDate.now(KST).minusDays(1));

        // 이미 제출을 받지 않는 링크를 닫는 건 성립하지 않는 요청이라, 사유를 구분해 거절한다.
        closeLink(linkId, owner, "CLOSED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R014"))
                .andExpect(jsonPath("$.message").value("유효 기간이 지난 링크입니다."));

        // 거절 경로는 상태를 건드리지 않는다 — 저장된 상태 정리는 재발급·본문 수정 시점에 이뤄진다.
        assertThat(statusOf(linkId)).isEqualTo(ReviewLinkStatus.ACTIVE);
    }

    @Test
    @DisplayName("마지막 날 당일에는 아직 제출을 받는다 — 유효 기간은 그 날 끝까지다")
    void submissionIsAcceptedOnLastValidDay() throws Exception {
        User owner = persistUser("owner-lastday@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        LocalDate today = LocalDate.now(KST);
        String token = issueLinkExpiringOn(section.getId(), owner, today.plusDays(1));
        Long linkId = linkId(token);

        // 만료일을 "오늘"로 당긴다 — 하루를 먼저 닫아버리는 off-by-one 이 있으면 여기서 걸린다.
        expireBy(linkId, today);

        mockMvc.perform(get("/public/review-links/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.expiresOn").value(today.toString()));

        submitAsReviewer(token, "CLEAR", "browser-last-day")
                .andExpect(status().isCreated());

        // 팀장 화면에서도 아직 살아 있는 링크로 보여야 한다
        mockMvc.perform(get("/api/project-sections/{id}/review-links/current", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.expiresOn").value(today.toString()));
    }

    @Test
    @DisplayName("유효 기간이 날짜로 파싱되지 않으면 400(C001) 로 거부한다")
    void malformedExpiryIsRejected() throws Exception {
        User owner = persistUser("owner-expiry-format@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();

        // yyyy-MM-dd 가 아니거나 달력에 없는 날짜는 본문 파싱 단계에서 걸러진다.
        for (String malformed : java.util.List.of("2026-13-01", "2026/09/01", "20260901", "내일")) {
            mockMvc.perform(post("/api/project-sections/{id}/review-links", section.getId())
                            .with(authentication(authOf(owner)))
                            .contentType("application/json")
                            .content("{ \"expiresOn\": \"%s\" }".formatted(malformed)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        assertThat(reviewLinkRepository.count()).isZero();
    }

    @Test
    @DisplayName("본문 수정으로 먼저 만료된 링크는 기간이 지나도 OUTDATED 로 남는다 — 먼저 끝난 사유가 이긴다")
    void pastDueDoesNotOverwriteContentEditReason() throws Exception {
        User owner = persistUser("owner-outdated-then-pastdue@wevo.com", "팀장");
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        String token = issueLinkExpiringOn(
                section.getId(), owner, LocalDate.now(KST).plusDays(7));
        Long linkId = linkId(token);

        // 1) 기간이 남은 상태에서 본문이 수정돼 OUTDATED 가 된 뒤,
        reviewLinkService.markSectionLinksOutdated(section.getId());
        assertThat(statusOf(linkId)).isEqualTo(ReviewLinkStatus.OUTDATED);

        // 2) 나중에 유효 기간까지 지난다 (contentEditDoesNotOverwritePastDueReason 의 역순)
        expireBy(linkId, LocalDate.now(KST).minusDays(1));

        // 외부 검토자에게는 "본문이 바뀌었다"로 안내해야 한다 — EXPIRED 로 뒤바뀌면 안 된다.
        assertThat(statusOf(linkId)).isEqualTo(ReviewLinkStatus.OUTDATED);
        mockMvc.perform(get("/public/review-links/{token}", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R004"));
        submitAsReviewer(token, "CLEAR", "browser-outdated-then-pastdue")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R004"));
        closeLink(linkId, owner, "CLOSED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R012"));
    }

    @Test
    @DisplayName("활성이 아닌 링크는 열람과 제출이 같은 코드로 거절된다 — 세 종결 사유 전부")
    void viewAndSubmitRejectWithTheSameCode() throws Exception {
        User owner = persistUser("owner-same-code@wevo.com", "팀장");
        // 섹션당 ACTIVE 링크는 1개라, 세 종결 사유를 각각 다른 섹션에서 만든다.
        // (유효 기간 시나리오의 em.clear() 전에 시드를 모두 끝내둔다)
        Long outdatedSectionId = persistOwnedSectionWithDraft(owner);
        Long expiredSectionId = persistOwnedSectionWithDraft(owner);
        Long closedSectionId = persistOwnedSectionWithDraft(owner);

        // 1) 본문 수정으로 만료 → R004
        String outdatedToken = issueLink(outdatedSectionId, owner);
        reviewLinkService.markSectionLinksOutdated(outdatedSectionId);
        assertViewAndSubmitRejectWith(outdatedToken, "R004", "browser-same-code-outdated");

        // 2) 유효 기간 경과 → R013 (저장된 상태는 아직 ACTIVE — 날짜로만 판정되는 경로)
        String expiredToken = issueLinkExpiringOn(
                expiredSectionId, owner, LocalDate.now(KST).plusDays(1));
        Long expiredLinkId = linkId(expiredToken);
        expireBy(expiredLinkId, LocalDate.now(KST).minusDays(1));
        assertThat(statusOf(expiredLinkId)).isEqualTo(ReviewLinkStatus.ACTIVE);
        assertViewAndSubmitRejectWith(expiredToken, "R013", "browser-same-code-expired");

        // 3) 팀장의 수동 종료 → R005
        String closedToken = issueLink(closedSectionId, owner);
        closeLink(linkId(closedToken), owner, "CLOSED").andExpect(status().isOk());
        assertViewAndSubmitRejectWith(closedToken, "R005", "browser-same-code-closed");
    }

    // --- 시드 헬퍼 ---

    /**
     * 열람과 제출이 <b>같은 실패 코드</b>로 거절되는지 확인한다.
     *
     * <p>두 경로가 갈리면 검토자가 본문을 다 읽은 뒤에야 제출을 거절당한다. 열람 응답에는 스냅샷
     * 본문이 실리지 않아야 한다({@code data} 생략).
     */
    private void assertViewAndSubmitRejectWith(String token, String expectedCode,
                                               String reviewerLabel) throws Exception {
        mockMvc.perform(get("/public/review-links/{token}", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.data").doesNotExist());

        submitAsReviewer(token, "CLEAR", reviewerLabel)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(expectedCode));
    }

    /** 소유자가 OWNER 인 프로젝트·섹션·초안을 한 벌 만들고 섹션 ID 를 돌려준다. */
    private Long persistOwnedSectionWithDraft(User owner) {
        ProjectSection section = persistSectionWithDraft(persistProject(owner), owner);
        persistMember(section.getProject(), owner, ProjectMemberRole.OWNER);
        em.flush();
        return section.getId();
    }

    /** 유효 기간을 지정해 링크를 발급하고 원문 토큰을 돌려준다. */
    private String issueLinkExpiringOn(Long sectionId, User owner, LocalDate expiresOn)
            throws Exception {
        MvcResult issued = mockMvc.perform(post("/api/project-sections/{id}/review-links", sectionId)
                        .with(authentication(authOf(owner)))
                        .contentType("application/json")
                        .content("{ \"expiresOn\": \"%s\" }".formatted(expiresOn)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(issued.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    /**
     * 링크의 만료일을 과거로 당겨 "유효 기간이 지난 상태"를 만든다.
     *
     * <p>발급 API 는 미래 날짜만 받으므로(422 C002) 시간을 흘려보내는 대신 저장된 만료일을 바꾼다.
     * 상태값({@code status})은 건드리지 않아, 정리되지 않은 {@code ACTIVE} 행을 날짜로 판정하는
     * 실제 상황을 그대로 재현한다.
     */
    private void expireBy(Long reviewLinkId, LocalDate expiresOn) {
        em.flush();
        em.createNativeQuery("UPDATE review_links SET expires_on = :expiresOn WHERE id = :id")
                .setParameter("expiresOn", expiresOn)
                .setParameter("id", reviewLinkId)
                .executeUpdate();
        em.clear(); //1차 캐시의 낡은 엔티티를 비워 이후 조회가 DB 값을 읽게 한다
    }

    private org.springframework.test.web.servlet.ResultActions submitWithoutSummary(
            String token, String signal, String reviewerLabel) throws Exception {
        return mockMvc.perform(post("/public/review-links/{token}/submissions", token)
                .header(REVIEWER_ID_HEADER, reviewerId(reviewerLabel))
                .contentType("application/json")
                .content("{ \"understandingSignal\": \"%s\" }".formatted(signal)));
    }

    private org.springframework.test.web.servlet.ResultActions submitWithSummary(
            String token, String signal, String reviewerLabel, String summary) throws Exception {
        return mockMvc.perform(post("/public/review-links/{token}/submissions", token)
                .header(REVIEWER_ID_HEADER, reviewerId(reviewerLabel))
                .contentType("application/json")
                .content("{ \"understandingSignal\": \"%s\", \"summary\": \"%s\" }"
                        .formatted(signal, summary)));
    }

    private org.springframework.test.web.servlet.ResultActions submitAsReviewer(
            String token, String signal, String reviewerLabel) throws Exception {
        return mockMvc.perform(post("/public/review-links/{token}/submissions", token)
                .header(REVIEWER_ID_HEADER, reviewerId(reviewerLabel))
                .contentType("application/json")
                .content("{ \"understandingSignal\": \"%s\", \"summary\": \"핵심을 이해했어요.\" }".formatted(signal)));
    }

    private Long linkId(String token) {
        return reviewLinkRepository.findByTokenHash(tokenHasher.hash(token)).orElseThrow().getId();
    }

    private ReviewLinkStatus statusOf(Long reviewLinkId) {
        em.flush();
        return reviewLinkRepository.findById(reviewLinkId).orElseThrow().getStatus();
    }

    private org.springframework.test.web.servlet.ResultActions closeLink(
            Long reviewLinkId, User actor, String status) throws Exception {
        return mockMvc.perform(patch("/api/review-links/{id}", reviewLinkId)
                .with(authentication(authOf(actor)))
                .contentType("application/json")
                .content("{ \"status\": \"%s\" }".formatted(status)));
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
                .audience("외부 검토자")
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
        User confirmer = editor == null ? section.getProject().getOwner() : editor;
        em.persist(SectionAuthorIntent.confirmed(
                section,
                version,
                "정보가 흩어진 문제를 해결하는 것이 핵심이다.",
                confirmer,
                LocalDateTime.now()));
        em.flush();
    }
}
