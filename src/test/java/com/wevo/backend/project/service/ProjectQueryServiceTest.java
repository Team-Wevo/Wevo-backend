package com.wevo.backend.project.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.dto.request.ProjectSearchCondition;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.dto.response.ProjectDetailResponse;
import com.wevo.backend.project.dto.response.ProjectSummaryResponse;
import com.wevo.backend.project.dto.response.SectionSummaryResponse;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import com.wevo.backend.project.repository.ProjectRepository;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.domain.SectionTemplate;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.section.repository.SectionTemplateRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 프로젝트/섹션 조회 로직 테스트. (권한: 멤버가 아니면 404)
 */
@ExtendWith(MockitoExtension.class)
class ProjectQueryServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PROJECT_ID = 100L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private ProjectSectionRepository projectSectionRepository;
    @Mock
    private SectionTemplateRepository sectionTemplateRepository;

    @InjectMocks
    private ProjectService projectService;

    @Test
    @DisplayName("내가 멤버인 프로젝트 목록을 내 역할과 함께 반환한다")
    void getMyProjects_returnsProjectsWithMyRole() {
        ProjectMember membership = membership(ProjectMemberRole.OWNER);
        given(projectMemberRepository.findAllWithProjectByUserId(USER_ID)).willReturn(List.of(membership));
        given(projectSectionRepository.findAllByProjectIdsOrderByLastActivity(List.of(PROJECT_ID)))
                .willReturn(sectionsOf(membership.getProject(),
                        ProjectSectionStatus.CONFIRMED, ProjectSectionStatus.COLLECTING));

        List<ProjectSummaryResponse> projects = projectService.getMyProjects(USER_ID, ProjectSearchCondition.none());

        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).projectId()).isEqualTo(PROJECT_ID);
        assertThat(projects.get(0).myRole()).isEqualTo(ProjectMemberRole.OWNER);
        assertThat(projects.get(0).resultType()).isEqualTo(OutputType.PRESENTATION);
    }

    @Test
    @DisplayName("마지막 활동 섹션은 활동 시각이 가장 늦은 섹션이다 — 진행 순서가 아니라 손댄 순서")
    void getMyProjects_picksMostRecentlyTouchedSection() {
        ProjectMember membership = membership(ProjectMemberRole.OWNER);
        // 4번까지 진행한 뒤 1번을 다시 고친 상황. 리포지토리가 활동 시각 내림차순으로 돌려준다.
        List<ProjectSection> sections = sectionsOf(membership.getProject(),
                ProjectSectionStatus.COLLECTING,  // order 1 — 방금 고침
                ProjectSectionStatus.CONFIRMED,   // order 2
                ProjectSectionStatus.CONFIRMED,   // order 3
                ProjectSectionStatus.DRAFTING);   // order 4
        given(projectMemberRepository.findAllWithProjectByUserId(USER_ID)).willReturn(List.of(membership));
        given(projectSectionRepository.findAllByProjectIdsOrderByLastActivity(List.of(PROJECT_ID)))
                .willReturn(sections);

        List<ProjectSummaryResponse> projects = projectService.getMyProjects(USER_ID, ProjectSearchCondition.none());

        assertThat(projects.get(0).lastActiveSection().order()).isEqualTo(1);
        assertThat(projects.get(0).lastActiveSection().title()).isEqualTo("섹션 1");
        assertThat(projects.get(0).lastActiveSection().sectionStatus())
                .isEqualTo(ProjectSectionStatus.COLLECTING);
        assertThat(projects.get(0).sectionProgress().total()).isEqualTo(4);
        assertThat(projects.get(0).sectionProgress().confirmed()).isEqualTo(2);
    }

    @Test
    @DisplayName("목록은 마지막 활동이 늦은 프로젝트부터 반환한다 — 생성 순서가 아니다")
    void getMyProjects_sortsByRecentWorkNotCreation() {
        ProjectMember older = membership(ProjectMemberRole.OWNER, 100L, "먼저 만든 프로젝트");
        ProjectMember newer = membership(ProjectMemberRole.MEMBER, 200L, "나중에 만든 프로젝트");

        // 먼저 만든 쪽을 방금 건드렸다 — 생성 순서로 정렬하면 뒤로 밀리는 배치다.
        ProjectSection touchedNow = section(older.getProject(), 1, ProjectSectionStatus.DRAFTING,
                LocalDateTime.of(2026, 8, 5, 18, 0));
        ProjectSection touchedEarlier = section(newer.getProject(), 1, ProjectSectionStatus.COLLECTING,
                LocalDateTime.of(2026, 8, 1, 9, 0));

        given(projectMemberRepository.findAllWithProjectByUserId(USER_ID))
                .willReturn(List.of(newer, older));
        given(projectSectionRepository.findAllByProjectIdsOrderByLastActivity(List.of(200L, 100L)))
                .willReturn(List.of(touchedNow, touchedEarlier));

        List<ProjectSummaryResponse> projects = projectService.getMyProjects(USER_ID, ProjectSearchCondition.none());

        assertThat(projects).extracting(ProjectSummaryResponse::title)
                .containsExactly("먼저 만든 프로젝트", "나중에 만든 프로젝트");
    }

    @Test
    @DisplayName("프로젝트가 없으면 섹션을 조회하지 않고 빈 목록을 반환한다")
    void getMyProjects_noProjects_skipsSectionQuery() {
        given(projectMemberRepository.findAllWithProjectByUserId(USER_ID)).willReturn(List.of());

        assertThat(projectService.getMyProjects(USER_ID, ProjectSearchCondition.none())).isEmpty();

        verify(projectSectionRepository, never()).findAllByProjectIdsOrderByLastActivity(any());
    }

    @Test
    @DisplayName("프로젝트가 여러 개여도 섹션 조회는 한 번만 한다 (N+1 방지)")
    void getMyProjects_readsSectionsInOneQuery() {
        ProjectMember first = membership(ProjectMemberRole.OWNER, 100L, "A");
        ProjectMember second = membership(ProjectMemberRole.OWNER, 200L, "B");
        given(projectMemberRepository.findAllWithProjectByUserId(USER_ID))
                .willReturn(List.of(first, second));
        given(projectSectionRepository.findAllByProjectIdsOrderByLastActivity(List.of(100L, 200L)))
                .willReturn(List.of(
                        section(first.getProject(), 1, ProjectSectionStatus.COLLECTING,
                                LocalDateTime.of(2026, 8, 5, 10, 0)),
                        section(second.getProject(), 1, ProjectSectionStatus.COLLECTING,
                                LocalDateTime.of(2026, 8, 4, 10, 0))));

        projectService.getMyProjects(USER_ID, ProjectSearchCondition.none());

        verify(projectSectionRepository, times(1)).findAllByProjectIdsOrderByLastActivity(any());
    }

    @Test
    @DisplayName("검색어로 거르면 걸러진 프로젝트의 섹션은 조회하지 않는다")
    void getMyProjects_filtersBeforeSectionQuery() {
        ProjectMember hit = membership(ProjectMemberRole.OWNER, 100L, "위보 발표 준비");
        ProjectMember miss = membership(ProjectMemberRole.OWNER, 200L, "동아리 제안서");
        given(projectMemberRepository.findAllWithProjectByUserId(USER_ID))
                .willReturn(List.of(hit, miss));
        given(projectSectionRepository.findAllByProjectIdsOrderByLastActivity(List.of(100L)))
                .willReturn(List.of(section(hit.getProject(), 1, ProjectSectionStatus.COLLECTING,
                        LocalDateTime.of(2026, 8, 5, 10, 0))));

        List<ProjectSummaryResponse> projects = projectService.getMyProjects(
                USER_ID, new ProjectSearchCondition("발표", null, null));

        assertThat(projects).extracting(ProjectSummaryResponse::title)
                .containsExactly("위보 발표 준비");
        // 걸러낸 프로젝트의 ID 가 섹션 조회에 섞이면 안 된다.
        verify(projectSectionRepository).findAllByProjectIdsOrderByLastActivity(List.of(100L));
    }

    @Test
    @DisplayName("조건에 맞는 프로젝트가 없으면 섹션을 조회하지 않고 빈 목록을 반환한다")
    void getMyProjects_noMatch_skipsSectionQuery() {
        given(projectMemberRepository.findAllWithProjectByUserId(USER_ID))
                .willReturn(List.of(membership(ProjectMemberRole.OWNER, 100L, "위보 발표 준비")));

        List<ProjectSummaryResponse> projects = projectService.getMyProjects(
                USER_ID, new ProjectSearchCondition("없는키워드", null, null));

        assertThat(projects).isEmpty();
        verify(projectSectionRepository, never()).findAllByProjectIdsOrderByLastActivity(any());
    }

    @Test
    @DisplayName("프로젝트 상세는 내 역할·멤버 수·섹션 진행 요약을 포함한다")
    void getProject_returnsDetailWithProgress() {
        given(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .willReturn(Optional.of(membership(ProjectMemberRole.MEMBER)));
        given(projectMemberRepository.countByProjectId(PROJECT_ID)).willReturn(3L);
        // 상세는 진행도만 필요하므로 템플릿을 조인하지 않는 조회를 쓴다
        given(projectSectionRepository.findByProjectIdOrderBySectionOrder(PROJECT_ID))
                .willReturn(sections(ProjectSectionStatus.CONFIRMED, ProjectSectionStatus.COLLECTING));

        ProjectDetailResponse detail = projectService.getProject(USER_ID, PROJECT_ID);

        assertThat(detail.projectId()).isEqualTo(PROJECT_ID);
        assertThat(detail.myRole()).isEqualTo(ProjectMemberRole.MEMBER);
        assertThat(detail.memberCount()).isEqualTo(3L);
        assertThat(detail.sectionProgress().total()).isEqualTo(2);
        assertThat(detail.sectionProgress().confirmed()).isEqualTo(1);
    }

    @Test
    @DisplayName("섹션 목록은 순서대로, 템플릿의 핵심 질문·가이드와 함께 반환한다")
    void getSections_returnsSectionsWithTemplateContent() {
        given(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .willReturn(Optional.of(membership(ProjectMemberRole.OWNER)));
        given(projectSectionRepository.findAllWithTemplateByProjectId(PROJECT_ID))
                .willReturn(sections(ProjectSectionStatus.COLLECTING, ProjectSectionStatus.COLLECTING));

        List<SectionSummaryResponse> sections = projectService.getSections(USER_ID, PROJECT_ID);

        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).keyQuestion()).isEqualTo("핵심 질문 1");
        assertThat(sections.get(0).guide()).isEqualTo("작성 가이드 1");
        assertThat(sections.get(0).sectionStatus()).isEqualTo(ProjectSectionStatus.COLLECTING);
    }

    @Test
    @DisplayName("멤버가 아니면 상세 조회 시 PROJECT_NOT_FOUND(404) 를 던진다 — 존재 자체를 숨긴다")
    void getProject_notMember_throwsProjectNotFound() {
        given(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID)).willReturn(Optional.empty());

        BusinessException exception =
                assertThrows(BusinessException.class, () -> projectService.getProject(USER_ID, PROJECT_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
    }

    @Test
    @DisplayName("멤버가 아니면 섹션 조회도 PROJECT_NOT_FOUND(404) 를 던진다")
    void getSections_notMember_throwsProjectNotFound() {
        given(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID)).willReturn(Optional.empty());

        BusinessException exception =
                assertThrows(BusinessException.class, () -> projectService.getSections(USER_ID, PROJECT_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
    }

    // ── 픽스처 ──

    private ProjectMember membership(ProjectMemberRole role) {
        return membership(role, PROJECT_ID, "위보 발표 준비");
    }

    private ProjectMember membership(ProjectMemberRole role, Long projectId, String title) {
        User user = User.builder().name("호석").status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", USER_ID);

        Project project = Project.builder()
                .owner(user).title(title).ideaText("아이디어")
                .resultType(OutputType.PRESENTATION).audience("심사위원")
                .status(ProjectStatus.ACTIVE).build();
        ReflectionTestUtils.setField(project, "id", projectId);

        return ProjectMember.builder().project(project).user(user).role(role).build();
    }

    /** 리포지토리가 활동 시각 내림차순으로 돌려준다는 계약을 그대로 흉내 낸다. */
    private List<ProjectSection> sectionsOf(Project project, ProjectSectionStatus... statuses) {
        List<ProjectSection> result = new java.util.ArrayList<>();
        for (int i = 0; i < statuses.length; i++) {
            result.add(section(project, i + 1, statuses[i],
                    LocalDateTime.of(2026, 8, 5, 12, 0).minusMinutes(i)));
        }
        return result;
    }

    private ProjectSection section(Project project, int order, ProjectSectionStatus status,
                                   LocalDateTime lastActivityAt) {
        SectionTemplate template = SectionTemplate.builder()
                .resultType(OutputType.PRESENTATION)
                .sectionKey("key-" + order)
                .title("섹션 " + order)
                .description("핵심 질문 " + order)
                .guideText("작성 가이드 " + order)
                .orderNo(order)
                .isRequired(true)
                .build();

        ProjectSection section = ProjectSection.builder()
                .project(project).template(template)
                .title(template.getTitle()).sectionOrder(order)
                .status(status)
                .build();
        ReflectionTestUtils.setField(section, "id", (long) order);
        // recordActivity 는 과거 시각을 무시하므로(마지막 활동이 뒤로 밀리지 않게) 직접 세팅한다.
        ReflectionTestUtils.setField(section, "lastActivityAt", lastActivityAt);
        return section;
    }

    private List<ProjectSection> sections(ProjectSectionStatus... statuses) {
        Project project = Project.builder().title("p").build();
        List<ProjectSection> result = new java.util.ArrayList<>();
        for (int i = 0; i < statuses.length; i++) {
            SectionTemplate template = SectionTemplate.builder()
                    .resultType(OutputType.PRESENTATION)
                    .sectionKey("key-" + (i + 1))
                    .title("섹션 " + (i + 1))
                    .description("핵심 질문 " + (i + 1))
                    .guideText("작성 가이드 " + (i + 1))
                    .orderNo(i + 1)
                    .isRequired(true)
                    .build();
            result.add(ProjectSection.builder()
                    .project(project).template(template)
                    .title(template.getTitle()).sectionOrder(i + 1)
                    .status(statuses[i])
                    .build());
        }
        return result;
    }
}
