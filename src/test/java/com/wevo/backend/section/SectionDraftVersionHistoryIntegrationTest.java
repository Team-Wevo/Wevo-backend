package com.wevo.backend.section;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.wevo.backend.section.domain.DraftLease;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionDraft;
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
 * 초안 버전 이력 조회(목록·버전 본문)를 실제 PostgreSQL 로 검증한다. (API_SPEC §3.7.8·§3.7.9)
 *
 * <p>이 기능이 막으려는 사고는 <b>덮어쓰기로 인한 내용 유실</b>이다 — 초안 저장은 본문 전체를
 * 교체하므로 실수로 지운 문단은 최신 본문만 보는 화면에서 되찾을 수 없다. 따라서 핵심 검증은
 * "덮어쓴 뒤에도 지난 버전의 본문이 그대로 읽히는가"이며, 저장 API 를 실제로 호출해 확인한다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
@AutoConfigureMockMvc
@Transactional
class SectionDraftVersionHistoryIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("이력은 최신 버전이 먼저 오고 본문 대신 메타데이터만 담는다")
    void listsVersionsNewestFirstWithoutContent() throws Exception {
        User owner = persistUser("owner-history-1@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project);
        persistDraft(section, "1차 초안", 1, owner);
        persistDraft(section, "2차 초안 본문", 2, owner);
        em.flush();
        em.clear();

        getVersions(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.versions[0].version").value(2))
                .andExpect(jsonPath("$.data.versions[1].version").value(1))
                .andExpect(jsonPath("$.data.versions[0].contentLength").value("2차 초안 본문".length()))
                .andExpect(jsonPath("$.data.versions[0].editor.userId").value(owner.getId()))
                .andExpect(jsonPath("$.data.versions[0].editor.name").value("owner-history-1"))
                .andExpect(jsonPath("$.data.versions[0].savedAt").exists())
                // 목록은 어느 버전을 열어볼지 고르는 용도라 본문을 싣지 않는다
                .andExpect(jsonPath("$.data.versions[0].content").doesNotExist());
    }

    @Test
    @DisplayName("덮어써서 지운 문단도 지난 버전 본문 조회로 그대로 되찾을 수 있다")
    void overwrittenContentRemainsReadableInPastVersion() throws Exception {
        User owner = persistUser("owner-history-2@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project);
        em.flush();

        saveDraft(section.getId(), owner, "꼭 남겨야 하는 문단과 나머지 본문", 0);
        // 편집권을 잡아야 기존 초안을 수정할 수 있다 (§5.2.1)
        persistLease(section, owner, LocalDateTime.now(KST).plusMinutes(5));
        em.flush();
        saveDraft(section.getId(), owner, "실수로 다 지움", 1);
        em.flush();
        em.clear();

        // 최신 본문에는 지운 문단이 없다
        getVersion(section.getId(), 2, owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("실수로 다 지움"));

        // 직전 버전에는 그대로 남아 있어 사용자가 복사해 되살릴 수 있다
        getVersion(section.getId(), 1, owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.content").value("꼭 남겨야 하는 문단과 나머지 본문"))
                .andExpect(jsonPath("$.data.editor.userId").value(owner.getId()))
                .andExpect(jsonPath("$.data.savedAt").exists());
    }

    @Test
    @DisplayName("본문 길이가 급감한 지점을 목록만 보고도 짚을 수 있다")
    void contentLengthRevealsWhereContentWasLost() throws Exception {
        User owner = persistUser("owner-history-3@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project);
        persistDraft(section, "아주 길게 쓴 본문입니다", 1, owner);
        persistDraft(section, "짧음", 2, owner);
        em.flush();
        em.clear();

        getVersions(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versions[0].contentLength").value("짧음".length()))
                .andExpect(jsonPath("$.data.versions[1].contentLength").value("아주 길게 쓴 본문입니다".length()));
    }

    @Test
    @DisplayName("저장된 버전이 없으면 404가 아니라 빈 목록으로 응답한다")
    void emptyHistoryIsNotAnError() throws Exception {
        User owner = persistUser("owner-history-4@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project);
        em.flush();
        em.clear();

        getVersions(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.versions").isEmpty());
    }

    @Test
    @DisplayName("편집자 기록이 없는 버전도 목록에서 빠지지 않고 editor 키만 생략된다")
    void versionWithoutEditorStaysInHistory() throws Exception {
        User owner = persistUser("owner-history-5@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project);
        persistDraft(section, "편집자 없는 본문", 1, null);
        em.flush();
        em.clear();

        getVersions(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.versions[0].version").value(1))
                .andExpect(jsonPath("$.data.versions[0].editor").doesNotExist());
    }

    @Test
    @DisplayName("탈퇴한 편집자는 대체 표시 이름으로 남는다")
    void withdrawnEditorKeepsPlaceholderName() throws Exception {
        User owner = persistUser("owner-history-6@wevo.com");
        User leaver = persistUser("leaver-history-6@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, leaver, ProjectMemberRole.MEMBER);
        ProjectSection section = persistSection(project);
        persistDraft(section, "탈퇴자가 쓴 본문", 1, leaver);
        leaver.withdraw();
        em.flush();
        em.clear();

        getVersions(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versions[0].editor.userId").value(leaver.getId()))
                .andExpect(jsonPath("$.data.versions[0].editor.name").value(User.WITHDRAWN_NAME));
    }

    @Test
    @DisplayName("없는 버전을 조회하면 404(S003)")
    void unknownVersion() throws Exception {
        User owner = persistUser("owner-history-7@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project);
        persistDraft(section, "본문", 1, owner);
        em.flush();
        em.clear();

        getVersion(section.getId(), 99, owner)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("S003"));
    }

    @Test
    @DisplayName("비참여자에게는 이력도 버전 본문도 섹션 존재를 숨겨 404(S001)")
    void nonMemberHiddenAsNotFound() throws Exception {
        User owner = persistUser("owner-history-8@wevo.com");
        User outsider = persistUser("outsider-history-8@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project);
        persistDraft(section, "남의 프로젝트 본문", 1, owner);
        em.flush();
        em.clear();

        getVersions(section.getId(), outsider)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S001"));

        getVersion(section.getId(), 1, outsider)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S001"));
    }

    @Test
    @DisplayName("다른 섹션의 버전 번호로는 조회되지 않는다")
    void versionIsScopedToItsSection() throws Exception {
        User owner = persistUser("owner-history-9@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection sectionA = persistSection(project, 1);
        ProjectSection sectionB = persistSection(project, 2);
        persistDraft(sectionA, "A 섹션 본문", 1, owner);
        em.flush();
        em.clear();

        getVersion(sectionB.getId(), 1, owner)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S003"));
    }

    @Test
    @DisplayName("이력 조회는 편집권을 요구하지도 발급하지도 않는다")
    void readingHistoryDoesNotTouchLease() throws Exception {
        User owner = persistUser("owner-history-10@wevo.com");
        User editor = persistUser("editor-history-10@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, editor, ProjectMemberRole.MEMBER);
        ProjectSection section = persistSection(project);
        persistDraft(section, "본문", 1, editor);
        // 타인이 편집 중이어도 지난 본문을 읽는 것은 방해가 되지 않는다
        persistLease(section, editor, LocalDateTime.now(KST).plusMinutes(5));
        em.flush();
        em.clear();

        getVersions(section.getId(), owner).andExpect(status().isOk());
        getVersion(section.getId(), 1, owner).andExpect(status().isOk());
    }

    // --- 헬퍼 ---

    private ResultActions getVersions(Long sectionId, User user) throws Exception {
        return mockMvc.perform(get("/api/project-sections/{id}/draft/versions", sectionId)
                .with(authentication(authOf(user))));
    }

    private ResultActions getVersion(Long sectionId, int version, User user) throws Exception {
        return mockMvc.perform(
                get("/api/project-sections/{id}/draft/versions/{version}", sectionId, version)
                        .with(authentication(authOf(user))));
    }

    private void saveDraft(Long sectionId, User user, String content, int baseVersion) throws Exception {
        mockMvc.perform(put("/api/project-sections/{id}/draft", sectionId)
                        .with(authentication(authOf(user)))
                        .contentType("application/json")
                        .content("{ \"content\": \"" + content + "\", \"baseVersion\": " + baseVersion + " }"))
                .andExpect(status().isOk());
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

    private ProjectSection persistSection(Project project) {
        return persistSection(project, 1);
    }

    /** 같은 프로젝트에 섹션을 둘 이상 만들 때는 순서를 다르게 준다 — (project_id, section_order) 가 유니크다. */
    private ProjectSection persistSection(Project project, int order) {
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .title("문제 정의")
                .sectionOrder(order)
                .status(ProjectSectionStatus.DRAFTING)
                .build();
        em.persist(section);
        return section;
    }

    private void persistDraft(ProjectSection section, String content, int version, User editor) {
        em.persist(SectionDraft.builder()
                .projectSection(section)
                .content(content)
                .version(version)
                .lastEditor(editor)
                .build());
    }

    private void persistLease(ProjectSection section, User holder, LocalDateTime leaseUntil) {
        em.persist(DraftLease.builder()
                .projectSection(section)
                .holderUserId(holder.getId())
                .leaseUntil(leaseUntil)
                .build());
    }
}
