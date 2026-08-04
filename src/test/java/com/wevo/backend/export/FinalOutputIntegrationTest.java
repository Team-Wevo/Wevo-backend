package com.wevo.backend.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.global.security.AuthPrincipal;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionDraft;
import com.wevo.backend.section.service.ConfirmedSectionContent;
import com.wevo.backend.section.service.SectionConfirmationQueryService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최종 결과물 조회 전 경로를 실제 PostgreSQL 컨텍스트로 검증한다. (API_SPEC §3.6.1)
 *
 * <p>확정(confirm) API가 아직 없어 확정 상태·{@code confirmedVersion} 을 EntityManager 로 직접 시드한다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
@AutoConfigureMockMvc
@Transactional
class FinalOutputIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private ProjectAccessGuard projectAccessGuard;

    @Autowired
    private SectionConfirmationQueryService sectionConfirmationQueryService;

    @Test
    @DisplayName("전 섹션이 확정되면 확정본을 섹션 순서대로 조립해 반환한다")
    void allConfirmedReturnsAssembledOutput() throws Exception {
        User owner = persistUser("owner@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);

        // 순서를 뒤섞어 저장해도 sectionOrder 기준으로 정렬되는지 함께 검증한다.
        ProjectSection second = persistSection(project, "해결 방안", 2, ProjectSectionStatus.CONFIRMED, 1);
        ProjectSection first = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 1);
        persistDraft(second, "해결 방안 확정본", 1, owner);
        persistDraft(first, "문제 정의 확정본", 1, owner);
        flushAndClear();

        getFinalOutput(project.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.confirmedCount").value(2))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.title").value("위보 기획"))
                .andExpect(jsonPath("$.data.resultType").value("PRESENTATION"))
                .andExpect(jsonPath("$.data.sections.length()").value(2))
                .andExpect(jsonPath("$.data.sections[0].order").value(1))
                .andExpect(jsonPath("$.data.sections[0].title").value("문제 정의"))
                .andExpect(jsonPath("$.data.sections[0].content").value("문제 정의 확정본"))
                .andExpect(jsonPath("$.data.sections[1].order").value(2))
                .andExpect(jsonPath("$.data.sections[1].content").value("해결 방안 확정본"));
    }

    @Test
    @DisplayName("확정 후 초안이 더 저장돼도 최신본이 아니라 확정본(confirmedVersion)을 반환한다")
    void returnsConfirmedVersionNotLatest() throws Exception {
        User owner = persistUser("owner@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);

        ProjectSection section = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 2);
        persistDraft(section, "1차 초안", 1, owner);
        persistDraft(section, "확정된 본문", 2, owner);
        persistDraft(section, "확정 후 수정된 최신본", 3, owner);
        flushAndClear();

        getFinalOutput(project.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.sections.length()").value(1))
                .andExpect(jsonPath("$.data.sections[0].content").value("확정된 본문"));
    }

    @Test
    @DisplayName("일부 섹션이 미확정이면 ready=false 와 진행도만 반환하고 본문은 담지 않는다")
    void partiallyConfirmedReturnsProgressOnly() throws Exception {
        User owner = persistUser("owner@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);

        ProjectSection confirmed = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 1);
        persistSection(project, "해결 방안", 2, ProjectSectionStatus.REVIEWING, 0);
        persistDraft(confirmed, "문제 정의 확정본", 1, owner);
        flushAndClear();

        getFinalOutput(project.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.confirmedCount").value(1))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                // 정책서 §2.3 — 부분 완성본을 제공하지 않는다
                .andExpect(jsonPath("$.data.sections").doesNotExist());
    }

    @Test
    @DisplayName("확정된 섹션이 하나도 없으면 ready=false 와 진행도 0 을 반환한다")
    void noneConfirmedReturnsZeroProgress() throws Exception {
        User owner = persistUser("owner@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);

        persistSection(project, "문제 정의", 1, ProjectSectionStatus.COLLECTING, 0);
        flushAndClear();

        getFinalOutput(project.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.confirmedCount").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.sections").doesNotExist());
    }

    @Test
    @DisplayName("팀원(MEMBER)도 최종 결과물을 조회할 수 있다")
    void memberCanRead() throws Exception {
        User owner = persistUser("owner@wevo.com");
        User member = persistUser("member@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, member, ProjectMemberRole.MEMBER);

        ProjectSection section = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 1);
        persistDraft(section, "문제 정의 확정본", 1, owner);
        flushAndClear();

        getFinalOutput(project.getId(), member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(true))
                .andExpect(jsonPath("$.data.sections[0].content").value("문제 정의 확정본"));
    }

    @Test
    @DisplayName("확정 섹션 수와 확정본 수가 어긋나면 부분 완성본 대신 500(E001) 로 실패한다")
    void integrityMismatchFailsAsServerError() throws Exception {
        User owner = persistUser("owner@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);

        // 확정 버전은 2 인데 이력에는 1 밖에 없는 정합성 붕괴 상태 — 확정 처리와 초안 이력이 어긋났다.
        ProjectSection section = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 2);
        persistDraft(section, "1차 초안", 1, owner);
        flushAndClear();

        // 클라이언트가 요청을 바꿔 해결할 수 있는 상태 충돌(409)이 아니라 서버 측 데이터 오류다.
        getFinalOutput(project.getId(), owner)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("E001"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("확정 해제된 섹션은 confirmedVersion 이 남아 있어도 확정본 조회에서 제외된다")
    void reopenedSectionIsExcludedFromConfirmedContents() {
        User owner = persistUser("owner@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);

        ProjectSection confirmed = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 1);
        // 확정 후 다시 열린 섹션 — 상태만 되돌아가고 confirmedVersion 은 남아 있다.
        ProjectSection reopened = persistSection(project, "해결 방안", 2, ProjectSectionStatus.DRAFTING, 1);
        persistDraft(confirmed, "문제 정의 확정본", 1, owner);
        persistDraft(reopened, "확정 해제된 지난 본문", 1, owner);
        flushAndClear();

        VerifiedProjectAccess access =
                projectAccessGuard.requireParticipantAccess(project.getId(), owner.getId());

        assertThat(sectionConfirmationQueryService.findConfirmedContents(access))
                .extracting(ConfirmedSectionContent::content)
                .containsExactly("문제 정의 확정본");
    }

    @Test
    @DisplayName("프로젝트 멤버가 아니면 404(P001) — 프로젝트 존재를 숨긴다")
    void nonMemberHiddenAsNotFound() throws Exception {
        User owner = persistUser("owner@wevo.com");
        User outsider = persistUser("outsider@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        flushAndClear();

        getFinalOutput(project.getId(), outsider)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("P001"));
    }

    @Test
    @DisplayName("존재하지 않는 프로젝트를 조회하면 404(P001)")
    void unknownProject() throws Exception {
        User user = persistUser("user@wevo.com");
        flushAndClear();

        getFinalOutput(999_999L, user)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P001"));
    }

    @Test
    @DisplayName("전 섹션 확정 시 일반 텍스트 복사본을 조립해 반환한다")
    void copiesAsPlainText() throws Exception {
        User owner = persistUser("owner@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);

        ProjectSection second = persistSection(project, "해결 방안", 2, ProjectSectionStatus.CONFIRMED, 1);
        ProjectSection first = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 1);
        persistDraft(second, "해결 방안 확정본", 1, owner);
        persistDraft(first, "문제 정의 확정본", 1, owner);
        flushAndClear();

        getCopy(project.getId(), owner, "plain-text")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.content").value("""
                        위보 기획

                        01. 문제 정의

                        문제 정의 확정본

                        02. 해결 방안

                        해결 방안 확정본"""));
    }

    @Test
    @DisplayName("전 섹션 확정 시 마크다운 복사본을 조립해 반환한다")
    void copiesAsMarkdown() throws Exception {
        User owner = persistUser("owner@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);

        ProjectSection first = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 1);
        persistDraft(first, "문제 정의 확정본", 1, owner);
        flushAndClear();

        getCopy(project.getId(), owner, "markdown")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("""
                        # 위보 기획

                        ## 01. 문제 정의

                        문제 정의 확정본"""));
    }

    @Test
    @DisplayName("미확정 섹션이 있으면 복사본을 만들지 않고 409(C003) 로 거절한다")
    void partiallyConfirmedIsRejectedForCopy() throws Exception {
        User owner = persistUser("owner@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);

        ProjectSection confirmed = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 1);
        persistSection(project, "해결 방안", 2, ProjectSectionStatus.REVIEWING, 0);
        persistDraft(confirmed, "문제 정의 확정본", 1, owner);
        flushAndClear();

        // 조회 API 와 같은 기준 — 일부만 담긴 문자열이 완성본으로 오인돼 공유되는 것을 막는다.
        for (String format : new String[]{"plain-text", "markdown"}) {
            getCopy(project.getId(), owner, format)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("C003"));
        }
    }

    @Test
    @DisplayName("팀원(MEMBER)도 복사본을 조회할 수 있다")
    void memberCanCopy() throws Exception {
        User owner = persistUser("owner@wevo.com");
        User member = persistUser("member@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, member, ProjectMemberRole.MEMBER);

        ProjectSection first = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 1);
        persistDraft(first, "문제 정의 확정본", 1, owner);
        flushAndClear();

        getCopy(project.getId(), member, "plain-text")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isNotEmpty());
    }

    @Test
    @DisplayName("프로젝트 멤버가 아니면 복사도 404(P001) — 프로젝트 존재를 숨긴다")
    void nonMemberCannotCopy() throws Exception {
        User owner = persistUser("owner@wevo.com");
        User stranger = persistUser("stranger@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);

        ProjectSection first = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 1);
        persistDraft(first, "문제 정의 확정본", 1, owner);
        flushAndClear();

        getCopy(project.getId(), stranger, "markdown")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P001"));
    }

    @Test
    @DisplayName("전 섹션 확정 시 일반 텍스트 파일로 다운로드된다")
    void downloadsAsPlainTextFile() throws Exception {
        User owner = persistUser("owner@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);

        ProjectSection second = persistSection(project, "해결 방안", 2, ProjectSectionStatus.CONFIRMED, 1);
        ProjectSection first = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 1);
        persistDraft(second, "해결 방안 확정본", 1, owner);
        persistDraft(first, "문제 정의 확정본", 1, owner);
        flushAndClear();

        // 복사본(§3.6.2)과 완전히 같은 본문이어야 한다 — 조립 규칙이 갈리면 여기서 깨진다.
        getDownload(project.getId(), owner, "plain-text")
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"final-output.txt\"; "
                                + "filename*=UTF-8''%EC%9C%84%EB%B3%B4%20%EA%B8%B0%ED%9A%8D.txt"))
                .andExpect(content().string("""
                        위보 기획

                        01. 문제 정의

                        문제 정의 확정본

                        02. 해결 방안

                        해결 방안 확정본"""));
    }

    @Test
    @DisplayName("전 섹션 확정 시 마크다운 파일로 다운로드된다")
    void downloadsAsMarkdownFile() throws Exception {
        User owner = persistUser("owner@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);

        ProjectSection first = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 1);
        persistDraft(first, "문제 정의 확정본", 1, owner);
        flushAndClear();

        getDownload(project.getId(), owner, "markdown")
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"final-output.md\"; "
                                + "filename*=UTF-8''%EC%9C%84%EB%B3%B4%20%EA%B8%B0%ED%9A%8D.md"))
                .andExpect(content().string("""
                        # 위보 기획

                        ## 01. 문제 정의

                        문제 정의 확정본"""));
    }

    @Test
    @DisplayName("fileName 을 지정하면 프로젝트 제목 대신 그 이름으로 내려받는다")
    void downloadsWithRequestedFileName() throws Exception {
        User owner = persistUser("owner@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);

        ProjectSection first = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 1);
        persistDraft(first, "문제 정의 확정본", 1, owner);
        flushAndClear();

        mockMvc.perform(get("/api/projects/{id}/final-output/download/plain-text", project.getId())
                        .param("fileName", "최종 제안서")
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"final-output.txt\"; "
                                + "filename*=UTF-8''%EC%B5%9C%EC%A2%85%20%EC%A0%9C%EC%95%88%EC%84%9C.txt"))
                // 파일명만 바뀌고 본문은 그대로다
                .andExpect(content().string(containsString("문제 정의 확정본")));
    }

    @Test
    @DisplayName("쓸 수 없는 fileName 이 와도 거부하지 않고 안전한 이름으로 바꿔 내려받는다")
    void unusableRequestedFileNameIsSanitizedNotRejected() throws Exception {
        User owner = persistUser("owner@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);

        ProjectSection first = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 1);
        persistDraft(first, "문제 정의 확정본", 1, owner);
        flushAndClear();

        // 경로 탈출·헤더 주입 시도가 헤더에 그대로 실리지 않아야 한다.
        mockMvc.perform(get("/api/projects/{id}/final-output/download/markdown", project.getId())
                        .param("fileName", "../../etc/passwd")
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"etc passwd.md\"; "
                                + "filename*=UTF-8''etc%20passwd.md"));
    }

    @Test
    @DisplayName("같은 형식이면 복사 API 와 다운로드 API 의 본문이 완전히 같다")
    void copyAndDownloadShareTheSameBody() throws Exception {
        User owner = persistUser("owner@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);

        ProjectSection second = persistSection(project, "해결 방안", 2, ProjectSectionStatus.CONFIRMED, 1);
        ProjectSection first = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 1);
        persistDraft(second, "해결 방안 확정본\n둘째 문단", 1, owner);
        persistDraft(first, "문제 정의 확정본", 1, owner);
        flushAndClear();

        // 두 경로가 조립 로직을 공유한다는 계약을 실제 응답으로 못 박는다.
        for (String format : new String[]{"plain-text", "markdown"}) {
            String copied = objectMapper
                    .readTree(getCopy(project.getId(), owner, format)
                            .andExpect(status().isOk())
                            .andReturn().getResponse().getContentAsString())
                    .path("data").path("content").asText();
            String downloaded = getDownload(project.getId(), owner, format)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(downloaded)
                    .as("%s: 복사본과 다운로드 본문이 갈리면 안 된다", format)
                    .isEqualTo(copied);
        }
    }

    @Test
    @DisplayName("미확정 섹션이 있으면 파일 대신 409(C003) JSON 으로 거절한다")
    void partiallyConfirmedIsRejectedForDownload() throws Exception {
        User owner = persistUser("owner@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);

        ProjectSection confirmed = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 1);
        persistSection(project, "해결 방안", 2, ProjectSectionStatus.REVIEWING, 0);
        persistDraft(confirmed, "문제 정의 확정본", 1, owner);
        flushAndClear();

        // 성공은 파일, 실패는 JSON 인 혼합 계약 (CLAUDE.md §5.3)
        for (String format : new String[]{"plain-text", "markdown"}) {
            getDownload(project.getId(), owner, format)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("C003"))
                    .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
        }
    }

    @Test
    @DisplayName("확정본 정합성이 깨지면 파일 대신 500(E001) JSON 으로 실패한다")
    void integrityMismatchFailsDownloadAsServerError() throws Exception {
        User owner = persistUser("owner@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);

        // 확정 버전은 2 인데 이력에는 1 밖에 없는 정합성 붕괴 상태 (§3.6.1 과 같은 조건)
        ProjectSection section = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 2);
        persistDraft(section, "1차 초안", 1, owner);
        flushAndClear();

        // 일부가 빠진 결과물을 파일로 내보내지 않는다 — 실패는 파일이 아니라 JSON 이어야 한다.
        for (String format : new String[]{"plain-text", "markdown"}) {
            getDownload(project.getId(), owner, format)
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("E001"))
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
        }
    }

    @Test
    @DisplayName("팀원(MEMBER)도 완성본을 다운로드할 수 있다")
    void memberCanDownload() throws Exception {
        User owner = persistUser("owner@wevo.com");
        User member = persistUser("member@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, member, ProjectMemberRole.MEMBER);

        ProjectSection first = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 1);
        persistDraft(first, "문제 정의 확정본", 1, owner);
        flushAndClear();

        getDownload(project.getId(), member, "plain-text")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("문제 정의 확정본")));
    }

    @Test
    @DisplayName("프로젝트 멤버가 아니면 다운로드도 404(P001) — 프로젝트 존재를 숨긴다")
    void nonMemberCannotDownload() throws Exception {
        User owner = persistUser("owner@wevo.com");
        User stranger = persistUser("stranger@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);

        ProjectSection first = persistSection(project, "문제 정의", 1, ProjectSectionStatus.CONFIRMED, 1);
        persistDraft(first, "문제 정의 확정본", 1, owner);
        flushAndClear();

        getDownload(project.getId(), stranger, "markdown")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P001"));
    }

    // --- 헬퍼 ---

    private ResultActions getFinalOutput(Long projectId, User user) throws Exception {
        return mockMvc.perform(get("/api/projects/{id}/final-output", projectId)
                .with(authentication(authOf(user))));
    }

    private ResultActions getCopy(Long projectId, User user, String format) throws Exception {
        return mockMvc.perform(get("/api/projects/{id}/final-output/{format}", projectId, format)
                .with(authentication(authOf(user))));
    }

    private ResultActions getDownload(Long projectId, User user, String format) throws Exception {
        return mockMvc.perform(
                get("/api/projects/{id}/final-output/download/{format}", projectId, format)
                        .with(authentication(authOf(user))));
    }

    private UsernamePasswordAuthenticationToken authOf(User user) {
        return new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(user.getId()), null, AuthorityUtils.NO_AUTHORITIES);
    }

    /** 시드가 실제 SQL 로 반영된 뒤 조회되도록 영속성 컨텍스트를 비운다. */
    private void flushAndClear() {
        em.flush();
        em.clear();
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
                .audience("팀 검토자")
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

    private ProjectSection persistSection(Project project, String title, int order,
                                          ProjectSectionStatus status, int confirmedVersion) {
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .title(title)
                .sectionOrder(order)
                .status(status)
                .confirmedVersion(confirmedVersion)
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
}
