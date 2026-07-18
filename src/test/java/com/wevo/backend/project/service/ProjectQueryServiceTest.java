package com.wevo.backend.project.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

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

        List<ProjectSummaryResponse> projects = projectService.getMyProjects(USER_ID);

        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).projectId()).isEqualTo(PROJECT_ID);
        assertThat(projects.get(0).myRole()).isEqualTo(ProjectMemberRole.OWNER);
        assertThat(projects.get(0).resultType()).isEqualTo(OutputType.PRESENTATION);
    }

    @Test
    @DisplayName("프로젝트 상세는 내 역할·멤버 수·섹션 진행 요약을 포함한다")
    void getProject_returnsDetailWithProgress() {
        given(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .willReturn(Optional.of(membership(ProjectMemberRole.MEMBER)));
        given(projectMemberRepository.countByProjectId(PROJECT_ID)).willReturn(3L);
        given(projectSectionRepository.findAllWithTemplateByProjectId(PROJECT_ID))
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
        User user = User.builder().name("호석").status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", USER_ID);

        Project project = Project.builder()
                .owner(user).title("위보 발표 준비").ideaText("아이디어")
                .resultType(OutputType.PRESENTATION).audience("심사위원")
                .status(ProjectStatus.ACTIVE).build();
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);

        return ProjectMember.builder().project(project).user(user).role(role).build();
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
                    .status(statuses[i]).needsReReview(false)
                    .build());
        }
        return result;
    }
}
