package com.wevo.backend.section;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최신 초안 조회 전 경로를 실제 컨텍스트(H2)로 검증한다. (API_SPEC §3.7.1)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SectionDraftReadIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("여러 버전이 쌓여 있으면 최신 버전의 본문·버전·저장시각을 반환한다")
    void returnsLatestDraft() throws Exception {
        User owner = persistUser("owner-read-1@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project);
        persistDraft(section, "1차 초안", 1, owner);
        persistDraft(section, "최신 본문", 2, owner);
        em.flush();
        em.clear();

        getDraft(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.content").value("최신 본문"))
                .andExpect(jsonPath("$.data.contentVersion").value(2))
                .andExpect(jsonPath("$.data.updatedAt").exists())
                // 편집권을 잡은 사람이 없으면 activeEditor 는 생략된다
                .andExpect(jsonPath("$.data.activeEditor").doesNotExist());
    }

    @Test
    @DisplayName("편집권을 보유한 사용자가 있으면 activeEditor 로 함께 내려준다")
    void includesActiveEditor() throws Exception {
        User owner = persistUser("owner-read-2@wevo.com");
        User editor = persistUser("editor-read-2@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, editor, ProjectMemberRole.MEMBER);
        ProjectSection section = persistSection(project);
        persistDraft(section, "본문", 1, owner);
        persistLease(section, editor, LocalDateTime.now(KST).plusMinutes(5));
        em.flush();
        em.clear();

        getDraft(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeEditor.userId").value(editor.getId()))
                .andExpect(jsonPath("$.data.activeEditor.name").value("editor-read-2"));
    }

    @Test
    @DisplayName("편집권이 만료됐으면 activeEditor 를 내리지 않는다")
    void expiredLeaseIsNotActiveEditor() throws Exception {
        User owner = persistUser("owner-read-3@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project);
        persistDraft(section, "본문", 1, owner);
        persistLease(section, owner, LocalDateTime.now(KST).minusSeconds(1));
        em.flush();
        em.clear();

        getDraft(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeEditor").doesNotExist());
    }

    @Test
    @DisplayName("저장한 초안을 곧바로 조회하면 같은 본문·버전이 돌아온다")
    void savedDraftIsReadBack() throws Exception {
        User owner = persistUser("owner-read-5@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project);
        em.flush();

        mockMvc.perform(put("/api/project-sections/{id}/draft", section.getId())
                        .with(authentication(authOf(owner)))
                        .contentType("application/json")
                        .content("{ \"content\": \"직접 쓴 본문\", \"baseVersion\": 0 }"))
                .andExpect(status().isOk());
        em.flush();
        em.clear();

        getDraft(section.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("직접 쓴 본문"))
                .andExpect(jsonPath("$.data.contentVersion").value(1));
    }

    @Test
    @DisplayName("초안이 아직 없으면 404(S003)")
    void draftNotFound() throws Exception {
        User owner = persistUser("owner-read-6@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project);
        em.flush();

        getDraft(section.getId(), owner)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("S003"));
    }

    @Test
    @DisplayName("프로젝트 참여자가 아니면 404(S001) — 섹션 존재를 숨긴다")
    void nonMemberHiddenAsNotFound() throws Exception {
        User owner = persistUser("owner-read-7@wevo.com");
        User outsider = persistUser("outsider-read-7@wevo.com");
        Project project = persistProject(owner);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        ProjectSection section = persistSection(project);
        persistDraft(section, "본문", 1, owner);
        em.flush();

        getDraft(section.getId(), outsider)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S001"));
    }

    @Test
    @DisplayName("존재하지 않는 섹션을 조회하면 404(S001)")
    void unknownSection() throws Exception {
        User user = persistUser("user-read-8@wevo.com");
        em.flush();

        getDraft(999_999L, user)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S001"));
    }

    // --- 헬퍼 ---

    private ResultActions getDraft(Long sectionId, User user) throws Exception {
        return mockMvc.perform(get("/api/project-sections/{id}/draft", sectionId)
                .with(authentication(authOf(user))));
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
                .project(project).user(user).role(role).joinedAt(LocalDateTime.now(KST)).build();
        em.persist(member);
        return member;
    }

    private ProjectSection persistSection(Project project) {
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .title("문제 정의")
                .sectionOrder(1)
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
