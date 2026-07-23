package com.wevo.backend.section;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.wevo.backend.section.repository.DraftLeaseRepository;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 JPA 매핑과 섹션 행 잠금 경로를 포함한 편집 잠금 API 통합 테스트.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
@AutoConfigureMockMvc
@Transactional
class DraftLeaseIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DraftLeaseRepository draftLeaseRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("프로젝트 참여자는 편집 가능한 초안에 대해 5분 편집 잠금을 획득한다")
    void participantAcquiresSingleLease() throws Exception {
        User owner = persistUser("owner-lease-1@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistDraft(section, owner);
        em.flush();
        LocalDateTime before = LocalDateTime.now(KST);

        mockMvc.perform(post("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DRAFT_LEASE_ACQUIRED"))
                .andExpect(jsonPath("$.data.expiresAt").exists())
                .andExpect(jsonPath("$.data.holder").doesNotExist());

        DraftLease lease = draftLeaseRepository.findByProjectSection_Id(section.getId()).orElseThrow();
        assertThat(lease.getHolderUserId()).isEqualTo(owner.getId());
        assertThat(lease.getLeaseUntil()).isAfterOrEqualTo(before.plusMinutes(5));
    }

    @Test
    @DisplayName("다른 사용자의 활성 편집 잠금이 있으면 errors 없이 S004로 거부한다")
    void activeLeaseHeldByOtherIsRejected() throws Exception {
        User owner = persistUser("owner-lease-2@wevo.com");
        User member = persistUser("member-lease-2@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, member, ProjectMemberRole.MEMBER);
        persistDraft(section, owner);
        em.flush();

        mockMvc.perform(post("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(member))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S004"))
                .andExpect(jsonPath("$.errors").doesNotExist());
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
        persistDraft(section, owner);
        em.persist(DraftLease.builder()
                .projectSection(section)
                .holderUserId(member.getId())
                .leaseUntil(LocalDateTime.now(KST).minusSeconds(1))
                .build());
        em.flush();

        mockMvc.perform(post("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresAt").exists());

        DraftLease lease = draftLeaseRepository.findByProjectSection_Id(section.getId()).orElseThrow();
        assertThat(lease.getHolderUserId()).isEqualTo(owner.getId());
    }

    @Test
    @DisplayName("초안이 없으면 편집 잠금 획득을 S003으로 거부한다")
    void draftMissingIsRejected() throws Exception {
        User owner = persistUser("owner-lease-4@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.flush();

        mockMvc.perform(post("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S003"));

        assertThat(draftLeaseRepository.findByProjectSection_Id(section.getId())).isEmpty();
    }

    @Test
    @DisplayName("COLLECTING 섹션은 초안이 있어도 편집 잠금 획득을 S002로 거부한다")
    void collectingSectionIsRejected() throws Exception {
        User owner = persistUser("owner-lease-5@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project, ProjectSectionStatus.COLLECTING);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistDraft(section, owner);
        em.flush();

        mockMvc.perform(post("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S002"));

        assertThat(draftLeaseRepository.findByProjectSection_Id(section.getId())).isEmpty();
    }

    @Test
    @DisplayName("유효한 편집 잠금을 조회하면 현재 편집자와 만료 시각을 반환한다")
    void getStatusReturnsActiveLease() throws Exception {
        User owner = persistUser("owner-lease-status-1@wevo.com");
        User member = persistUser("member-lease-status-1@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, member, ProjectMemberRole.MEMBER);
        em.persist(DraftLease.builder()
                .projectSection(section)
                .holderUserId(member.getId())
                .leaseUntil(LocalDateTime.now(KST).plusSeconds(30))
                .build());
        em.flush();

        mockMvc.perform(get("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.locked").value(true))
                .andExpect(jsonPath("$.data.editor.userId").value(member.getId()))
                .andExpect(jsonPath("$.data.editor.name").value(member.getName()))
                .andExpect(jsonPath("$.data.expiresAt").exists())
                .andExpect(jsonPath("$.data.lease").doesNotExist());
    }

    @Test
    @DisplayName("편집 잠금이 없으면 locked=false만 반환한다")
    void getStatusWithoutLeaseReturnsUnlocked() throws Exception {
        User owner = persistUser("owner-lease-status-2@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.flush();

        mockMvc.perform(get("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(false))
                .andExpect(jsonPath("$.data.editor").doesNotExist())
                .andExpect(jsonPath("$.data.expiresAt").doesNotExist());
    }

    @Test
    @DisplayName("만료된 편집 잠금은 locked=false로 반환하고 행은 보존한다")
    void getStatusWithExpiredLeaseReturnsUnlocked() throws Exception {
        User owner = persistUser("owner-lease-status-3@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        DraftLease expired = DraftLease.builder()
                .projectSection(section)
                .holderUserId(owner.getId())
                .leaseUntil(LocalDateTime.now(KST).minusSeconds(1))
                .build();
        em.persist(expired);
        em.flush();

        mockMvc.perform(get("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(false))
                .andExpect(jsonPath("$.data.editor").doesNotExist())
                .andExpect(jsonPath("$.data.expiresAt").doesNotExist());

        assertThat(draftLeaseRepository.findById(expired.getId())).isPresent();
    }

    @Test
    @DisplayName("프로젝트 비멤버의 편집 잠금 조회는 섹션 존재를 숨겨 S001을 반환한다")
    void getStatusByNonMemberReturnsSectionNotFound() throws Exception {
        User owner = persistUser("owner-lease-status-4@wevo.com");
        User outsider = persistUser("outsider-lease-status-4@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.flush();

        mockMvc.perform(get("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(outsider))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S001"));
    }

    @Test
    @DisplayName("존재하지 않는 섹션의 편집 잠금 조회는 S001을 반환한다")
    void getStatusByMissingSectionReturnsSectionNotFound() throws Exception {
        User user = persistUser("member-lease-status-missing@wevo.com");
        em.flush();

        mockMvc.perform(get("/api/project-sections/{id}/draft/lease", Long.MAX_VALUE)
                        .with(authentication(authOf(user))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S001"));
    }

    @Test
    @DisplayName("편집 잠금 보유자가 갱신하면 만료 시각이 현재 시각부터 5분 연장된다")
    void holderRenewsActiveLease() throws Exception {
        User owner = persistUser("owner-lease-renew-1@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        DraftLease lease = DraftLease.builder()
                .projectSection(section)
                .holderUserId(owner.getId())
                .leaseUntil(LocalDateTime.now(KST).plusSeconds(10))
                .build();
        em.persist(lease);
        em.flush();

        LocalDateTime requestedAt = LocalDateTime.now(KST);

        mockMvc.perform(put("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DRAFT_LEASE_RENEWED"))
                .andExpect(jsonPath("$.data.expiresAt").exists())
                .andExpect(jsonPath("$.data.leaseId").doesNotExist())
                .andExpect(jsonPath("$.data.leaseUntil").doesNotExist());

        em.flush();
        em.clear();
        DraftLease renewed = draftLeaseRepository.findById(lease.getId()).orElseThrow();
        assertThat(renewed.getLeaseUntil()).isAfterOrEqualTo(requestedAt.plusMinutes(5));
    }

    @Test
    @DisplayName("다른 멤버가 보유한 편집 잠금을 갱신하면 S004를 반환한다")
    void nonHolderCannotRenewLease() throws Exception {
        User owner = persistUser("owner-lease-renew-2@wevo.com");
        User member = persistUser("member-lease-renew-2@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, member, ProjectMemberRole.MEMBER);
        DraftLease lease = DraftLease.builder()
                .projectSection(section)
                .holderUserId(owner.getId())
                .leaseUntil(LocalDateTime.now(KST).plusSeconds(30))
                .build();
        em.persist(lease);
        em.flush();

        mockMvc.perform(put("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(member))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S004"));
    }

    @Test
    @DisplayName("편집 잠금이 없으면 S005를 반환한다")
    void missingLeaseCannotBeRenewed() throws Exception {
        User owner = persistUser("owner-lease-renew-3@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.flush();

        mockMvc.perform(put("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S005"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("만료된 편집 잠금을 갱신하면 errors 없이 S005를 반환한다")
    void expiredLeaseCannotBeRenewed() throws Exception {
        User owner = persistUser("owner-lease-renew-4@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        DraftLease lease = DraftLease.builder()
                .projectSection(section)
                .holderUserId(owner.getId())
                .leaseUntil(LocalDateTime.now(KST).minusSeconds(1))
                .build();
        em.persist(lease);
        em.flush();

        mockMvc.perform(put("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S005"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("비멤버의 편집 잠금 갱신은 섹션 존재를 숨겨 S001을 반환한다")
    void nonMemberCannotDetectLeaseByRenewal() throws Exception {
        User owner = persistUser("owner-lease-renew-5@wevo.com");
        User outsider = persistUser("outsider-lease-renew-5@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.persist(DraftLease.builder()
                .projectSection(section)
                .holderUserId(owner.getId())
                .leaseUntil(LocalDateTime.now(KST).plusMinutes(1))
                .build());
        em.flush();

        mockMvc.perform(put("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(outsider))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S001"));
    }

    @Test
    @DisplayName("편집 잠금 보유자가 종료하면 lease가 비활성화되고 행은 보존된다")
    void holderReleasesActiveLease() throws Exception {
        User owner = persistUser("owner-lease-release-1@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        DraftLease lease = DraftLease.builder()
                .projectSection(section)
                .holderUserId(owner.getId())
                .leaseUntil(LocalDateTime.now(KST).plusMinutes(3))
                .build();
        em.persist(lease);
        em.flush();

        mockMvc.perform(delete("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DRAFT_LEASE_RELEASED"))
                .andExpect(jsonPath("$.message").value("편집을 종료했습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        // PostgreSQL timestamp의 microsecond 반올림 경계를 피하면서, 해제 전 +3분이던 lease가
        // 해제 직후 시점으로 당겨져 비활성화됐는지 검증한다.
        LocalDateTime afterRelease = LocalDateTime.now(KST);
        em.flush();
        em.clear();
        DraftLease released = draftLeaseRepository.findById(lease.getId()).orElseThrow();
        assertThat(released.isActiveAt(afterRelease.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("해제된 편집 잠금은 다른 프로젝트 참여자가 곧바로 재획득한다")
    void releasedLeaseCanBeReacquiredByOther() throws Exception {
        User owner = persistUser("owner-lease-release-2@wevo.com");
        User member = persistUser("member-lease-release-2@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, member, ProjectMemberRole.MEMBER);
        persistDraft(section, owner);
        em.persist(DraftLease.builder()
                .projectSection(section)
                .holderUserId(owner.getId())
                .leaseUntil(LocalDateTime.now(KST).plusMinutes(3))
                .build());
        em.flush();

        mockMvc.perform(delete("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresAt").exists());

        em.flush();
        em.clear();
        DraftLease lease = draftLeaseRepository.findByProjectSection_Id(section.getId()).orElseThrow();
        assertThat(lease.getHolderUserId()).isEqualTo(member.getId());
    }

    @Test
    @DisplayName("타인이 보유한 활성 편집 잠금은 강제 해제할 수 없어 S004를 반환한다")
    void nonHolderCannotReleaseLease() throws Exception {
        User owner = persistUser("owner-lease-release-3@wevo.com");
        User member = persistUser("member-lease-release-3@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, member, ProjectMemberRole.MEMBER);
        DraftLease lease = DraftLease.builder()
                .projectSection(section)
                .holderUserId(owner.getId())
                .leaseUntil(LocalDateTime.now(KST).plusSeconds(30))
                .build();
        em.persist(lease);
        em.flush();

        mockMvc.perform(delete("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(member))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S004"));

        em.flush();
        em.clear();
        DraftLease unchanged = draftLeaseRepository.findById(lease.getId()).orElseThrow();
        assertThat(unchanged.getHolderUserId()).isEqualTo(owner.getId());
        assertThat(unchanged.isActiveAt(LocalDateTime.now(KST))).isTrue();
    }

    @Test
    @DisplayName("편집 잠금이 없으면 종료 시 errors 없이 S005를 반환한다")
    void missingLeaseCannotBeReleased() throws Exception {
        User owner = persistUser("owner-lease-release-4@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.flush();

        mockMvc.perform(delete("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S005"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("만료된 편집 잠금을 종료하면 errors 없이 S005를 반환한다")
    void expiredLeaseCannotBeReleased() throws Exception {
        User owner = persistUser("owner-lease-release-5@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.persist(DraftLease.builder()
                .projectSection(section)
                .holderUserId(owner.getId())
                .leaseUntil(LocalDateTime.now(KST).minusSeconds(1))
                .build());
        em.flush();

        mockMvc.perform(delete("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S005"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("비멤버의 편집 종료는 섹션 존재를 숨겨 S001을 반환한다")
    void nonMemberCannotDetectLeaseByRelease() throws Exception {
        User owner = persistUser("owner-lease-release-6@wevo.com");
        User outsider = persistUser("outsider-lease-release-6@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        em.persist(DraftLease.builder()
                .projectSection(section)
                .holderUserId(owner.getId())
                .leaseUntil(LocalDateTime.now(KST).plusMinutes(1))
                .build());
        em.flush();

        mockMvc.perform(delete("/api/project-sections/{id}/draft/lease", section.getId())
                        .with(authentication(authOf(outsider))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("S001"));
    }

    @Test
    @DisplayName("의견 수집 재오픈 시 OWNER 본인의 활성 lease를 해제하고 COLLECTING으로 전이한다")
    void reopenOpinionGateReleasesOwnersLease() throws Exception {
        User owner = persistUser("owner-reopen-lease-1@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistDraft(section, owner);
        DraftLease lease = DraftLease.builder()
                .projectSection(section)
                .holderUserId(owner.getId())
                .leaseUntil(LocalDateTime.now(KST).plusMinutes(3))
                .build();
        em.persist(lease);
        em.flush();

        mockMvc.perform(post("/api/project-sections/{id}/opinion-gate/reopen", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OPINION_GATE_REOPENED"))
                .andExpect(jsonPath("$.data.sectionStatus").value("COLLECTING"));

        LocalDateTime afterReopen = LocalDateTime.now(KST);
        em.flush();
        em.clear();
        DraftLease released = draftLeaseRepository.findById(lease.getId()).orElseThrow();
        ProjectSection reopened = em.find(ProjectSection.class, section.getId());
        assertThat(released.isActiveAt(afterReopen.plusSeconds(1))).isFalse();
        assertThat(reopened.getStatus()).isEqualTo(ProjectSectionStatus.COLLECTING);
    }

    @Test
    @DisplayName("타인이 활성 lease를 보유하면 의견 수집 재오픈을 S004로 거부한다")
    void reopenOpinionGateRejectsOtherUsersLease() throws Exception {
        User owner = persistUser("owner-reopen-lease-2@wevo.com");
        User member = persistUser("member-reopen-lease-2@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, member, ProjectMemberRole.MEMBER);
        persistDraft(section, member);
        DraftLease lease = DraftLease.builder()
                .projectSection(section)
                .holderUserId(member.getId())
                .leaseUntil(LocalDateTime.now(KST).plusMinutes(3))
                .build();
        em.persist(lease);
        em.flush();

        mockMvc.perform(post("/api/project-sections/{id}/opinion-gate/reopen", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("S004"));

        assertThat(section.getStatus()).isEqualTo(ProjectSectionStatus.DRAFTING);
        assertThat(lease.isActiveAt(LocalDateTime.now(KST))).isTrue();
    }

    @Test
    @DisplayName("만료된 lease는 의견 수집 재오픈을 막지 않는다")
    void reopenOpinionGateIgnoresExpiredLease() throws Exception {
        User owner = persistUser("owner-reopen-lease-3@wevo.com");
        User member = persistUser("member-reopen-lease-3@wevo.com");
        Project project = persistProject(owner);
        ProjectSection section = persistSection(project, ProjectSectionStatus.REVIEWING);
        persistMember(project, owner, ProjectMemberRole.OWNER);
        persistMember(project, member, ProjectMemberRole.MEMBER);
        persistDraft(section, member);
        em.persist(DraftLease.builder()
                .projectSection(section)
                .holderUserId(member.getId())
                .leaseUntil(LocalDateTime.now(KST).minusSeconds(1))
                .build());
        em.flush();

        mockMvc.perform(post("/api/project-sections/{id}/opinion-gate/reopen", section.getId())
                        .with(authentication(authOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sectionStatus").value("COLLECTING"));
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
                .audience("프로젝트 팀원")
                .status(ProjectStatus.ACTIVE)
                .build();
        em.persist(project);
        return project;
    }

    private ProjectSection persistSection(Project project) {
        return persistSection(project, ProjectSectionStatus.DRAFTING);
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

    private void persistDraft(ProjectSection section, User editor) {
        em.persist(SectionDraft.builder()
                .projectSection(section)
                .content("초안 내용")
                .version(1)
                .lastEditor(editor)
                .build());
    }

    private void persistMember(Project project, User user, ProjectMemberRole role) {
        em.persist(ProjectMember.builder()
                .project(project)
                .user(user)
                .role(role)
                .joinedAt(LocalDateTime.now(KST))
                .build());
    }
}
