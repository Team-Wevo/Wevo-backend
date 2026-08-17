package com.wevo.backend.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.global.security.TokenHasher;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import com.wevo.backend.project.repository.ProjectRepository;
import com.wevo.backend.review.domain.ReviewIntentComparison;
import com.wevo.backend.review.domain.ReviewIntentComparisonStatus;
import com.wevo.backend.review.domain.ReviewLink;
import com.wevo.backend.review.domain.ReviewLinkStatus;
import com.wevo.backend.review.repository.ReviewIntentComparisonRepository;
import com.wevo.backend.review.repository.ReviewLinkRepository;
import com.wevo.backend.review.repository.ReviewSubmissionRepository;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionAuthorIntent;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.section.repository.SectionAuthorIntentRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 의도 비교 준비가 <b>제출 커밋 이후</b>에 실행된다는 계약을 실제 커밋까지 진행해 검증한다.
 *
 * <p>{@code ExternalReviewIntegrationTest} 는 {@code @Transactional} 로 롤백되어 커밋 이후 작업이
 * 아예 돌지 않으므로, 이 테스트만 트랜잭션 없이 실행하고 각 테스트가 스스로 정리한다.
 *
 * <p>핵심은 <b>격리 방향</b>이다 — 비교 준비는 제출 트랜잭션에 참여하지 않아야 한다.
 * 참여하면 준비 중 난 예외가 트랜잭션을 rollback-only 로 표시해, 호출자가 예외를 잡아도 커밋
 * 시점에 <b>정상 저장된 공개 제출까지</b> 롤백되고 검토자에게 500 이 나간다. 공개 제출은 다시
 * 요청할 수 없는 1회성 입력이라 이 되돌림을 허용하지 않는다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
@AutoConfigureMockMvc
class ExternalReviewComparisonPreparationIntegrationTest {

    private static final String REVIEWER_ID_HEADER = "X-Anonymous-Reviewer-Id";
    private static final String AUTHOR_INTENT = "정보가 흩어진 문제를 해결하는 것이 핵심이다.";
    private static final String CONTENT = "우리가 해결하려는 문제는 정보가 흩어져 있다는 점이다.";

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenHasher tokenHasher;
    @Autowired private ReviewIntentComparisonRepository comparisonRepository;
    @Autowired private ReviewSubmissionRepository submissionRepository;
    @Autowired private ReviewLinkRepository reviewLinkRepository;
    @Autowired private AiJobRepository aiJobRepository;
    @Autowired private SectionAuthorIntentRepository authorIntentRepository;
    @Autowired private SectionDraftRepository draftRepository;
    @Autowired private ProjectSectionRepository sectionRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;

    /**
     * 비교 준비 <b>안쪽</b>에서 나는 실패를 만들기 위해 감싼다. 해시가 형식을 어기면
     * {@code ReviewIntentComparison.pending} 검증이
     * {@code ReviewIntentComparisonStateService.preparePending}(MANDATORY) 안에서 터진다 —
     * 설정 오류·DB 오류로 같은 지점이 실패하는 상황과 같은 모양이며, 이 실패가 트랜잭션 롤백
     * 표시를 남기는 지점이다.
     */
    @MockitoSpyBean private AiInputSnapshotHasher snapshotHasher;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        comparisonRepository.deleteAll();
        submissionRepository.deleteAll();
        reviewLinkRepository.deleteAll();
        aiJobRepository.deleteAll();
        authorIntentRepository.deleteAll();
        draftRepository.deleteAll();
        sectionRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("제출이 커밋된 뒤 비교가 PENDING 으로 준비되고 AI 작업까지 연결된다")
    void comparisonIsPreparedAfterSubmissionCommit() throws Exception {
        String token = seedActiveLink();

        submit(token, "PARTIAL", "\"3번째 문단이 이해하기 어려웠어요.\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.comparison").doesNotExist());

        assertThat(comparisonRepository.findAll()).singleElement().satisfies(comparison -> {
            assertThat(comparison.getStatus()).isEqualTo(ReviewIntentComparisonStatus.PENDING);
            assertThat(comparison.getIntentSnapshotHash()).matches("[0-9a-f]{64}");
            assertThat(comparison.getReviewerSummaryHash()).matches("[0-9a-f]{64}");
            // 비교 저장 커밋 이후에 도는 AI 작업 생성까지 이어졌다는 뜻 — 커밋 이후 단계가
            // 두 번 연달아 있어도 뒤 단계가 유실되지 않아야 한다.
            assertThat(comparison.getSourceAiJobId()).isNotNull();
        });
    }

    @Test
    @DisplayName("대조할 이해가 없는 제출(UNCLEAR + summary 없음)은 NOT_AVAILABLE 로 준비된다")
    void comparisonIsNotAvailableWithoutReviewerSummary() throws Exception {
        String token = seedActiveLink();

        submit(token, "UNCLEAR", null).andExpect(status().isCreated());

        assertThat(comparisonRepository.findAll()).singleElement()
                .extracting(ReviewIntentComparison::getStatus)
                .isEqualTo(ReviewIntentComparisonStatus.NOT_AVAILABLE);
        // 대조할 문장이 없으므로 Provider 작업도 만들지 않는다.
        assertThat(aiJobRepository.count()).isZero();
    }

    @Test
    @DisplayName("비교 준비가 실패해도 이미 저장된 제출은 롤백되지 않고 201 로 끝난다")
    void comparisonPreparationFailureDoesNotRollBackCommittedSubmission() throws Exception {
        String token = seedActiveLink();
        doReturn("유효하지-않은-해시").when(snapshotHasher).hashCanonical(any());

        // 실패가 제출 트랜잭션에 번지면 커밋 시점에 UnexpectedRollbackException 이 나 500 이 된다.
        submit(token, "PARTIAL", "\"3번째 문단이 이해하기 어려웠어요.\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("REVIEW_SUBMITTED"));

        // 검토자의 답은 남고, 부가 기능인 비교만 조용히 빠진다.
        assertThat(submissionRepository.count()).isEqualTo(1);
        assertThat(comparisonRepository.findAll()).isEmpty();
    }

    private ResultActions submit(String token, String signal, String quotedSummary)
            throws Exception {
        String body = quotedSummary == null
                ? "{ \"understandingSignal\": \"%s\" }".formatted(signal)
                : "{ \"understandingSignal\": \"%s\", \"summary\": %s }"
                        .formatted(signal, quotedSummary);
        return mockMvc.perform(post("/public/review-links/{token}/submissions", token)
                .header(REVIEWER_ID_HEADER, UUID.randomUUID().toString())
                .contentType("application/json")
                .content(body));
    }

    /** 발급 API 를 거치지 않고 살아 있는 링크를 직접 시드하고 원문 토큰을 돌려준다. */
    private String seedActiveLink() {
        User owner = userRepository.save(User.builder()
                .name("팀장")
                .email(UUID.randomUUID() + "@wevo.com")
                .status(UserStatus.ACTIVE)
                .build());
        Project project = projectRepository.save(Project.builder()
                .owner(owner)
                .title("위보 기획")
                .resultType(OutputType.PROPOSAL)
                .audience("외부 검토자")
                .status(ProjectStatus.ACTIVE)
                .build());
        // 커밋 이후 AI 작업 생성이 요청자(발급자)의 OWNER 권한을 다시 검사한다.
        projectMemberRepository.save(ProjectMember.builder()
                .project(project)
                .user(owner)
                .role(ProjectMemberRole.OWNER)
                .joinedAt(LocalDateTime.now())
                .build());
        ProjectSection section = sectionRepository.save(ProjectSection.builder()
                .project(project)
                .title("문제 정의")
                .sectionOrder(1)
                .status(ProjectSectionStatus.REVIEWING)
                .build());

        draftRepository.save(SectionDraft.builder()
                .projectSection(section)
                .content(CONTENT)
                .version(1)
                .lastEditor(owner)
                .build());
        // 확정 의도는 스냅샷과 원본이 함께 있어야 한다 (chk_review_links_author_intent_pair).
        SectionAuthorIntent authorIntent = authorIntentRepository.save(
                SectionAuthorIntent.confirmed(section, 1, AUTHOR_INTENT, owner, LocalDateTime.now()));

        String rawToken = UUID.randomUUID().toString();
        reviewLinkRepository.save(ReviewLink.builder()
                .projectSection(section)
                .createdBy(owner)
                .tokenHash(tokenHasher.hash(rawToken))
                .sectionTitleSnapshot(section.getTitle())
                .contentSnapshot(CONTENT)
                .contentVersion(1)
                .authorIntentSnapshot(AUTHOR_INTENT)
                .authorIntent(authorIntent)
                .status(ReviewLinkStatus.ACTIVE)
                .build());
        return rawToken;
    }
}
