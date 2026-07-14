package com.wevo.backend.opinion.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionStatus;
import com.wevo.backend.opinion.dto.request.OpinionDraftRequest;
import com.wevo.backend.opinion.dto.response.MyOpinionResponse;
import com.wevo.backend.opinion.dto.response.OpinionDraftResponse;
import com.wevo.backend.opinion.repository.OpinionRepository;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OpinionServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PROJECT_ID = 100L;
    private static final Long SECTION_ID = 10L;
    private static final String CONTENT = "타겟을 공모전 참가 대학생 팀으로 좁히는 게 좋겠습니다.";

    @Mock
    private ProjectSectionRepository projectSectionRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private OpinionRepository opinionRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OpinionService opinionService;

    @Test
    @DisplayName("의견이 없으면 DRAFT 상태로 새로 생성한다")
    void saveDraft_createsNewDraft_whenAbsent() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        User author = user();
        given(projectSectionRepository.findByIdForUpdate(SECTION_ID)).willReturn(Optional.of(section));
        given(projectMemberRepository.existsByProjectIdAndUserId(PROJECT_ID, USER_ID)).willReturn(true);
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.empty());
        given(userRepository.getReferenceById(USER_ID)).willReturn(author);
        given(opinionRepository.save(any(Opinion.class))).willAnswer(invocation -> {
            Opinion opinion = invocation.getArgument(0);
            ReflectionTestUtils.setField(opinion, "id", 501L);
            return opinion;
        });

        OpinionDraftResponse response = opinionService.saveDraft(
                SECTION_ID, USER_ID, new OpinionDraftRequest(CONTENT));

        assertThat(response.id()).isEqualTo(501L);
        assertThat(response.content()).isEqualTo(CONTENT);
        assertThat(response.status()).isEqualTo(OpinionStatus.DRAFT);

        ArgumentCaptor<Opinion> captor = ArgumentCaptor.forClass(Opinion.class);
        verify(opinionRepository).save(captor.capture());
        assertThat(captor.getValue().getProjectSection()).isEqualTo(section);
        assertThat(captor.getValue().getAuthor()).isEqualTo(author);
    }

    @Test
    @DisplayName("기존 의견이 있으면 본문만 덮어쓰고 상태는 유지한다 (SUBMITTED 유지)")
    void saveDraft_overwritesContent_keepsStatus() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        Opinion existing = Opinion.builder()
                .projectSection(section)
                .author(user())
                .content("처음 작성했던 의견 내용입니다. 스무 자 이상.")
                .status(OpinionStatus.SUBMITTED)
                .build();
        ReflectionTestUtils.setField(existing, "id", 501L);
        given(projectSectionRepository.findByIdForUpdate(SECTION_ID)).willReturn(Optional.of(section));
        given(projectMemberRepository.existsByProjectIdAndUserId(PROJECT_ID, USER_ID)).willReturn(true);
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.of(existing));

        OpinionDraftResponse response = opinionService.saveDraft(
                SECTION_ID, USER_ID, new OpinionDraftRequest(CONTENT));

        assertThat(response.id()).isEqualTo(501L);
        assertThat(response.content()).isEqualTo(CONTENT);
        assertThat(response.status()).isEqualTo(OpinionStatus.SUBMITTED);
        verify(opinionRepository, never()).save(any(Opinion.class));
    }

    @Test
    @DisplayName("섹션이 없으면 SECTION_NOT_FOUND 예외를 던진다")
    void saveDraft_sectionNotFound_throws() {
        given(projectSectionRepository.findByIdForUpdate(SECTION_ID)).willReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.saveDraft(SECTION_ID, USER_ID, new OpinionDraftRequest(CONTENT)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_NOT_FOUND);
    }

    @Test
    @DisplayName("프로젝트 멤버가 아니면 NOT_PROJECT_MEMBER 예외를 던진다")
    void saveDraft_notMember_throws() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        given(projectSectionRepository.findByIdForUpdate(SECTION_ID)).willReturn(Optional.of(section));
        given(projectMemberRepository.existsByProjectIdAndUserId(PROJECT_ID, USER_ID)).willReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.saveDraft(SECTION_ID, USER_ID, new OpinionDraftRequest(CONTENT)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_PROJECT_MEMBER);
    }

    @Test
    @DisplayName("섹션이 의견 수집(COLLECTING) 단계가 아니면 OPINION_COLLECTION_CLOSED 예외를 던진다")
    void saveDraft_collectionClosed_throws() {
        ProjectSection section = section(ProjectSectionStatus.SYNTHESIZING);
        given(projectSectionRepository.findByIdForUpdate(SECTION_ID)).willReturn(Optional.of(section));
        given(projectMemberRepository.existsByProjectIdAndUserId(PROJECT_ID, USER_ID)).willReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.saveDraft(SECTION_ID, USER_ID, new OpinionDraftRequest(CONTENT)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.OPINION_COLLECTION_CLOSED);
    }

    @Test
    @DisplayName("내가 작성한 의견이 있으면 exists=true 와 의견 내용을 반환한다")
    void getMyOpinion_returnsOpinion_whenPresent() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        Opinion opinion = Opinion.builder()
                .projectSection(section)
                .author(user())
                .content(CONTENT)
                .status(OpinionStatus.DRAFT)
                .build();
        ReflectionTestUtils.setField(opinion, "id", 501L);
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectMemberRepository.existsByProjectIdAndUserId(PROJECT_ID, USER_ID)).willReturn(true);
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.of(opinion));

        MyOpinionResponse response = opinionService.getMyOpinion(SECTION_ID, USER_ID);

        assertThat(response.exists()).isTrue();
        assertThat(response.id()).isEqualTo(501L);
        assertThat(response.content()).isEqualTo(CONTENT);
        assertThat(response.status()).isEqualTo(OpinionStatus.DRAFT);
    }

    @Test
    @DisplayName("아직 의견을 작성하지 않았으면 예외 없이 exists=false 를 반환한다")
    void getMyOpinion_returnsEmpty_whenAbsent() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectMemberRepository.existsByProjectIdAndUserId(PROJECT_ID, USER_ID)).willReturn(true);
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.empty());

        MyOpinionResponse response = opinionService.getMyOpinion(SECTION_ID, USER_ID);

        assertThat(response.exists()).isFalse();
        assertThat(response.id()).isNull();
        assertThat(response.content()).isNull();
        assertThat(response.status()).isNull();
        assertThat(response.updatedAt()).isNull();
    }

    @Test
    @DisplayName("조회 시 섹션이 없으면 SECTION_NOT_FOUND 예외를 던진다")
    void getMyOpinion_sectionNotFound_throws() {
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.getMyOpinion(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_NOT_FOUND);
    }

    @Test
    @DisplayName("조회 시 프로젝트 멤버가 아니면 NOT_PROJECT_MEMBER 예외를 던진다")
    void getMyOpinion_notMember_throws() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectMemberRepository.existsByProjectIdAndUserId(PROJECT_ID, USER_ID)).willReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.getMyOpinion(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_PROJECT_MEMBER);
    }

    private ProjectSection section(ProjectSectionStatus status) {
        Project project = Project.builder()
                .owner(user())
                .title("발표 프로젝트")
                .status(ProjectStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);
        ProjectSection section = ProjectSection.builder()
                .project(project)
                .title("문제 정의")
                .sectionOrder(1)
                .status(status)
                .build();
        ReflectionTestUtils.setField(section, "id", SECTION_ID);
        return section;
    }

    private User user() {
        User user = User.builder().name("김민준").status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }
}
