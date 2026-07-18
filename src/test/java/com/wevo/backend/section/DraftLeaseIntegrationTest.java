package com.wevo.backend.section;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.wevo.backend.section.repository.DraftLeaseRepository;
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
 * 실제 JPA 매핑과 섹션 행 잠금 경로를 포함한 편집 잠금 API 통합 테스트.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DraftLeaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DraftLeaseRepository draftLeaseRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("프로젝트 참여자는 섹션당 하나의 60초 편집 잠금을 획득한다")
    void participantAcquiresSingleLease() throws Exception {
        User owner = persistUser("owner-lease-1@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.flush();

        mockMvc.perform(post("/api/project-sections/{id}/draft-lease/acquire", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("LEASE_ACQUIRED"))
                .andExpect(jsonPath("$.data.projectSectionId").value(section.getId()))
                .andExpect(jsonPath("$.data.holder.id").value(owner.getId()))
                .andExpect(jsonPath("$.data.leaseUntil").exists());

        DraftLease lease = draftLeaseRepository.findByProjectSection_Id(section.getId()).orElseThrow();
        assertThat(lease.getHolder().getId()).isEqualTo(owner.getId());
        assertThat(lease.getLeaseUntil()).isAfter(LocalDateTime.now().minusSeconds(1));
    }

    @Test
    @DisplayName("다른 사용자의 활성 편집 잠금이 있으면 S005로 거부한다")
    void activeLeaseHeldByOtherIsRejected() throws Exception {
        User owner = persistUser("owner-lease-2@wevo.com");
        User member = persistUser("member-lease-2@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, member, ProjectMemberRole.MEMBER);
        em.flush();

        mockMvc.perform(post("/api/project-sections/{id}/draft-lease/acquire", section.getId())
                        .with(authentication(authOf(member))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/project-sections/{id}/draft-lease/acquire", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S005"))
                .andExpect(jsonPath("$.errors[0].field").value("holder"));
    }

    @Test
    @DisplayName("만료된 편집 잠금은 다른 프로젝트 참여자가 재획득한다")
    void expiredLeaseCanBeReacquired() throws Exception {
        User owner = persistUser("owner-lease-3@wevo.com");
        User member = persistUser("member-lease-3@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, member, ProjectMemberRole.MEMBER);
        em.persist(DraftLease.builder()
                .projectSection(section)
                .holder(member)
                .leaseUntil(LocalDateTime.now().minusSeconds(1))
                .build());
        em.flush();

        mockMvc.perform(post("/api/project-sections/{id}/draft-lease/acquire", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.holder.id").value(owner.getId()));

        DraftLease lease = draftLeaseRepository.findByProjectSection_Id(section.getId()).orElseThrow();
        assertThat(lease.getHolder().getId()).isEqualTo(owner.getId());
    }

    private UsernamePasswordAuthenticationToken authOf(User user) {
        return new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(user.getId()), null, AuthorityUtils.NO_AUTHORITIES);
    }

    private User persistUser(String email) {
        User user = User.builder()
                .name(email.substring(0, email.indexOf('@')))
                .email(email)
                .status(UserStatus.ACTIVE)
                .build();
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

    private void persistMember(Project project, User user, ProjectMemberRole role) {
        em.persist(ProjectMember.builder()
                .project(project)
                .user(user)
                .role(role)
                .joinedAt(LocalDateTime.now())
                .build());
    }
}
