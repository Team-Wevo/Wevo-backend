package com.wevo.backend.opinion.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionStatus;
import com.wevo.backend.opinion.dto.request.OpinionDraftRequest;
import com.wevo.backend.opinion.dto.response.MyOpinionResponse;
import com.wevo.backend.opinion.dto.response.OpinionDraftResponse;
import com.wevo.backend.opinion.dto.response.OpinionSubmitResponse;
import com.wevo.backend.opinion.dto.response.SubmittedOpinionListResponse;
import com.wevo.backend.opinion.repository.OpinionRepository;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.project.service.ProjectAccessGuard;
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

import java.time.LocalDateTime;
import java.util.List;
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
    private ProjectAccessGuard projectAccessGuard;
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
        given(projectAccessGuard.requireParticipant(PROJECT_ID, USER_ID))
                .willThrow(new BusinessException(ErrorCode.NOT_PROJECT_MEMBER));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.saveDraft(SECTION_ID, USER_ID, new OpinionDraftRequest(CONTENT)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_PROJECT_MEMBER);
    }

    @Test
    @DisplayName("섹션이 의견 수집(COLLECTING) 단계가 아니면 OPINION_COLLECTION_CLOSED 예외를 던진다")
    void saveDraft_collectionClosed_throws() {
        ProjectSection section = section(ProjectSectionStatus.SYNTHESIZING);
        given(projectSectionRepository.findByIdForUpdate(SECTION_ID)).willReturn(Optional.of(section));

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
        given(projectAccessGuard.requireParticipant(PROJECT_ID, USER_ID))
                .willThrow(new BusinessException(ErrorCode.NOT_PROJECT_MEMBER));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.getMyOpinion(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_PROJECT_MEMBER);
    }

    @Test
    @DisplayName("임시저장된 의견을 SUBMITTED 상태로 제출하고 최초 제출 시각을 반환한다")
    void submitMyOpinion_submitsDraft() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        Opinion opinion = opinion(section, OpinionStatus.DRAFT, CONTENT, null);
        given(projectSectionRepository.findByIdForUpdate(SECTION_ID)).willReturn(Optional.of(section));
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.of(opinion));

        OpinionSubmitResponse response = opinionService.submitMyOpinion(SECTION_ID, USER_ID);

        assertThat(response.id()).isEqualTo(501L);
        assertThat(response.status()).isEqualTo(OpinionStatus.SUBMITTED);
        assertThat(response.submittedAt()).isNotNull();
        assertThat(opinion.getStatus()).isEqualTo(OpinionStatus.SUBMITTED);
        assertThat(opinion.getSubmittedAt()).isEqualTo(response.submittedAt());
    }

    @Test
    @DisplayName("이미 제출한 의견은 수집 마감 후 재호출해도 최초 제출 시각으로 멱등 성공한다")
    void submitMyOpinion_alreadySubmittedAfterClose_returnsIdempotentSuccess() {
        ProjectSection section = section(ProjectSectionStatus.SYNTHESIZING);
        LocalDateTime firstSubmittedAt = LocalDateTime.of(2026, 7, 14, 12, 5);
        Opinion opinion = opinion(section, OpinionStatus.SUBMITTED, CONTENT, firstSubmittedAt);
        given(projectSectionRepository.findByIdForUpdate(SECTION_ID)).willReturn(Optional.of(section));
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.of(opinion));

        OpinionSubmitResponse response = opinionService.submitMyOpinion(SECTION_ID, USER_ID);

        assertThat(response.status()).isEqualTo(OpinionStatus.SUBMITTED);
        assertThat(response.submittedAt()).isEqualTo(firstSubmittedAt);
        assertThat(opinion.getSubmittedAt()).isEqualTo(firstSubmittedAt);
    }

    @Test
    @DisplayName("미제출 의견은 수집이 마감된 섹션에 제출할 수 없다")
    void submitMyOpinion_collectionClosed_throws() {
        ProjectSection section = section(ProjectSectionStatus.SYNTHESIZING);
        Opinion opinion = opinion(section, OpinionStatus.DRAFT, CONTENT, null);
        given(projectSectionRepository.findByIdForUpdate(SECTION_ID)).willReturn(Optional.of(section));
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.of(opinion));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.submitMyOpinion(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.OPINION_COLLECTION_CLOSED);
    }

    @Test
    @DisplayName("수집 중이지만 임시저장된 의견이 없으면 OPINION_NOT_FOUND 예외를 던진다")
    void submitMyOpinion_opinionNotFound_throws() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        given(projectSectionRepository.findByIdForUpdate(SECTION_ID)).willReturn(Optional.of(section));
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.submitMyOpinion(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.OPINION_NOT_FOUND);
        assertThat(exception.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getField()).isEqualTo("projectSectionId");
            assertThat(error.getReason()).isEqualTo("no draft opinion to submit");
        });
    }

    @Test
    @DisplayName("저장된 의견 본문이 제출 기준을 위반하면 BUSINESS_RULE_VIOLATION 예외를 던진다")
    void submitMyOpinion_invalidContent_throws() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        Opinion opinion = opinion(section, OpinionStatus.DRAFT, "너무 짧은 의견", null);
        given(projectSectionRepository.findByIdForUpdate(SECTION_ID)).willReturn(Optional.of(section));
        given(opinionRepository.findByProjectSection_IdAndAuthor_Id(SECTION_ID, USER_ID))
                .willReturn(Optional.of(opinion));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.submitMyOpinion(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
        assertThat(exception.getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getField()).isEqualTo("content"));
    }

    @Test
    @DisplayName("본인이 제출했으면 제출 의견 전체를 제출 시각 순서 그대로 반환한다")
    void getSubmittedOpinions_returnsFullList_whenEverSubmitted() {
        ProjectSection section = section(ProjectSectionStatus.SYNTHESIZING); // 마감 후에도 조회 가능
        User other = user(2L, "이서연");
        Opinion mine = submittedOpinion(501L, section, user(),
                LocalDateTime.of(2026, 7, 14, 10, 20));
        Opinion others = submittedOpinion(508L, section, other,
                LocalDateTime.of(2026, 7, 14, 11, 0));
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(opinionRepository.findAllWithAuthorByProjectSectionIdAndStatus(
                SECTION_ID, OpinionStatus.SUBMITTED)).willReturn(List.of(mine, others));

        SubmittedOpinionListResponse response = opinionService.getSubmittedOpinions(SECTION_ID, USER_ID);

        assertThat(response.everSubmitted()).isTrue();
        assertThat(response.totalSubmittedCount()).isEqualTo(2);
        assertThat(response.opinions()).hasSize(2);
        assertThat(response.opinions().get(0).id()).isEqualTo(501L);
        assertThat(response.opinions().get(0).author().name()).isEqualTo("김민준");
        assertThat(response.opinions().get(1).id()).isEqualTo(508L);
        assertThat(response.opinions().get(1).author().id()).isEqualTo(2L);
        assertThat(response.opinions().get(1).submittedAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 14, 11, 0));
    }

    @Test
    @DisplayName("본인이 제출하지 않았으면 목록을 숨기고 제출 건수만 반환한다 (공개 게이트)")
    void getSubmittedOpinions_hidesList_whenNotSubmitted() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        Opinion others = submittedOpinion(508L, section, user(2L, "이서연"),
                LocalDateTime.of(2026, 7, 14, 11, 0));
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(opinionRepository.findAllWithAuthorByProjectSectionIdAndStatus(
                SECTION_ID, OpinionStatus.SUBMITTED)).willReturn(List.of(others));

        SubmittedOpinionListResponse response = opinionService.getSubmittedOpinions(SECTION_ID, USER_ID);

        assertThat(response.everSubmitted()).isFalse();
        assertThat(response.totalSubmittedCount()).isEqualTo(1);
        assertThat(response.opinions()).isEmpty();
    }

    @Test
    @DisplayName("목록 조회 시 섹션이 없으면 SECTION_NOT_FOUND 예외를 던진다")
    void getSubmittedOpinions_sectionNotFound_throws() {
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.getSubmittedOpinions(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_NOT_FOUND);
    }

    @Test
    @DisplayName("목록 조회 시 프로젝트 멤버가 아니면 NOT_PROJECT_MEMBER 예외를 던진다")
    void getSubmittedOpinions_notMember_throws() {
        ProjectSection section = section(ProjectSectionStatus.COLLECTING);
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectAccessGuard.requireParticipant(PROJECT_ID, USER_ID))
                .willThrow(new BusinessException(ErrorCode.NOT_PROJECT_MEMBER));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> opinionService.getSubmittedOpinions(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_PROJECT_MEMBER);
    }

    private Opinion submittedOpinion(Long id, ProjectSection section, User author,
                                     LocalDateTime submittedAt) {
        Opinion opinion = Opinion.builder()
                .projectSection(section)
                .author(author)
                .content(CONTENT)
                .status(OpinionStatus.SUBMITTED)
                .submittedAt(submittedAt)
                .build();
        ReflectionTestUtils.setField(opinion, "id", id);
        return opinion;
    }

    private Opinion opinion(ProjectSection section, OpinionStatus status, String content,
                            LocalDateTime submittedAt) {
        Opinion opinion = Opinion.builder()
                .projectSection(section)
                .author(user())
                .content(content)
                .status(status)
                .submittedAt(submittedAt)
                .build();
        ReflectionTestUtils.setField(opinion, "id", 501L);
        return opinion;
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
        return user(USER_ID, "김민준");
    }

    private User user(Long id, String name) {
        User user = User.builder().name(name).status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
