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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ReviewSubmissionRepository reviewSubmissionRepository;
    @Autowired
    private ReviewLinkRepository reviewLinkRepository;

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
        ReviewLink savedLink = reviewLinkRepository.findByToken(token).orElseThrow();
        assertThat(savedLink.getCreatedBy().getId()).isEqualTo(owner.getId());

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

    // --- 시드 헬퍼 ---

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

    private ProjectSection persistSectionWithDraft(Project project, User editor) {
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .title("문제 정의")
                .sectionOrder(1)
                .status(ProjectSectionStatus.REVIEWING)
                .needsReReview(false)
                .build();
        em.persist(section);

        SectionDraft draft = SectionDraft.builder()
                .projectSection(section)
                .content(DRAFT_CONTENT)
                .version(1)
                .lastEditor(editor)
                .build();
        em.persist(draft);
        return section;
    }
}
