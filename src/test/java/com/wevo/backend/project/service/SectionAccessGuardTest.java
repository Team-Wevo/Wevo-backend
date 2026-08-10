package com.wevo.backend.project.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.repository.ProjectSectionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class SectionAccessGuardTest {

    private static final Long PROJECT_ID = 10L;
    private static final Long SECTION_ID = 20L;
    private static final Long USER_ID = 1L;

    @Mock
    private ProjectSectionRepository projectSectionRepository;
    @Mock
    private ProjectAccessGuard projectAccessGuard;

    @InjectMocks
    private SectionAccessGuard sectionAccessGuard;

    private ProjectSection section;

    @BeforeEach
    void setUp() {
        Project project = Project.builder().title("프로젝트").build();
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);
        section = ProjectSection.builder().project(project).title("문제 정의").build();
    }

    @Test
    @DisplayName("프로젝트 참여자는 섹션 접근 검사를 통과한다")
    void requireParticipantSection_delegatesParticipantCheck() {
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));

        ProjectSection result = sectionAccessGuard.requireParticipantSection(SECTION_ID, USER_ID);

        assertThat(result).isSameAs(section);
        verify(projectAccessGuard).requireParticipant(PROJECT_ID, USER_ID);
    }

    @Test
    @DisplayName("역할 포함 참여자 검사는 멤버십을 한 번만 읽고 OWNER 여부를 함께 돌려준다")
    void requireParticipantSectionWithRole_returnsSectionAndRole() {
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectAccessGuard.requireParticipant(PROJECT_ID, USER_ID))
                .willReturn(membership(ProjectMemberRole.OWNER));

        VerifiedParticipantSection granted =
                sectionAccessGuard.requireParticipantSectionWithRole(SECTION_ID, USER_ID);

        assertThat(granted.section()).isSameAs(section);
        assertThat(granted.isOwner()).isTrue();
        // 역할까지 얻으려고 멤버십을 두 번 읽지 않는다 (존재 숨김 밖에서 403 이 새는 경로 차단)
        verify(projectAccessGuard).requireParticipant(PROJECT_ID, USER_ID);
        verifyNoMoreInteractions(projectAccessGuard);
    }

    @Test
    @DisplayName("역할 포함 참여자 검사에서 MEMBER 는 OWNER 가 아니다")
    void requireParticipantSectionWithRole_marksMemberAsNonOwner() {
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectAccessGuard.requireParticipant(PROJECT_ID, USER_ID))
                .willReturn(membership(ProjectMemberRole.MEMBER));

        assertThat(sectionAccessGuard.requireParticipantSectionWithRole(SECTION_ID, USER_ID).isOwner())
                .isFalse();
    }

    @Test
    @DisplayName("팀장 전용 섹션 검사는 OWNER 검사를 사용한다")
    void requireOwnedSection_delegatesOwnerCheck() {
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));

        assertThat(sectionAccessGuard.requireOwnedSection(SECTION_ID, USER_ID)).isSameAs(section);
        verify(projectAccessGuard).requireOwner(PROJECT_ID, USER_ID);
    }

    @Test
    @DisplayName("팀원 전용 섹션 검사는 MEMBER 검사를 사용한다")
    void requireMemberSection_delegatesMemberCheck() {
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));

        assertThat(sectionAccessGuard.requireMemberSection(SECTION_ID, USER_ID)).isSameAs(section);
        verify(projectAccessGuard).requireMember(PROJECT_ID, USER_ID);
    }

    @Test
    @DisplayName("쓰기 경로용 팀장 검사는 배타 잠금 조회를 사용하고 OWNER 검사를 수행한다")
    void requireOwnedSectionForUpdate_locksAndDelegatesOwnerCheck() {
        given(projectSectionRepository.findByIdForUpdate(SECTION_ID)).willReturn(Optional.of(section));

        assertThat(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, USER_ID)).isSameAs(section);
        verify(projectAccessGuard).requireOwner(PROJECT_ID, USER_ID);
    }

    @Test
    @DisplayName("쓰기 경로용 팀원 검사는 배타 잠금 조회를 사용하고 MEMBER 검사를 수행한다")
    void requireMemberSectionForUpdate_locksAndDelegatesMemberCheck() {
        given(projectSectionRepository.findByIdForUpdate(SECTION_ID)).willReturn(Optional.of(section));

        assertThat(sectionAccessGuard.requireMemberSectionForUpdate(SECTION_ID, USER_ID)).isSameAs(section);
        verify(projectAccessGuard).requireMember(PROJECT_ID, USER_ID);
    }

    @Test
    @DisplayName("참여자 공용 쓰기 경로 검사는 배타 잠금 조회를 사용하고 참여자 검사를 수행한다")
    void requireParticipantSectionForUpdate_locksAndDelegatesParticipantCheck() {
        given(projectSectionRepository.findByIdForUpdate(SECTION_ID)).willReturn(Optional.of(section));

        assertThat(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, USER_ID))
                .isSameAs(section);
        verify(projectAccessGuard).requireParticipant(PROJECT_ID, USER_ID);
    }

    @Test
    @DisplayName("섹션 기준 참여자 접근 증거 발급은 멤버십을 한 번만 조회한다")
    void requireParticipantAccessForSection_delegatesParticipantAccessOnce() {
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        VerifiedProjectAccess access = VerifiedProjectAccess.of(
                ProjectMember.builder()
                        .project(section.getProject())
                        .role(ProjectMemberRole.MEMBER)
                        .build());
        given(projectAccessGuard.requireParticipantAccess(PROJECT_ID, USER_ID)).willReturn(access);

        assertThat(sectionAccessGuard.requireParticipantAccessForSection(SECTION_ID, USER_ID))
                .isSameAs(access);
        // 증거를 발급하는 조회 하나로 끝내야 한다 — requireParticipant 를 덧대면 멤버십을 두 번 읽는다.
        verify(projectAccessGuard).requireParticipantAccess(PROJECT_ID, USER_ID);
        verifyNoMoreInteractions(projectAccessGuard);
    }

    @Test
    @DisplayName("섹션 기준 참여자 접근 증거 발급도 비멤버는 SECTION_NOT_FOUND 로 숨긴다")
    void requireParticipantAccessForSection_hidesNonMemberAsNotFound() {
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectAccessGuard.requireParticipantAccess(PROJECT_ID, USER_ID))
                .willThrow(new BusinessException(ErrorCode.NOT_PROJECT_MEMBER));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionAccessGuard.requireParticipantAccessForSection(SECTION_ID, USER_ID));

        // 숨김 밖에 두면 403 P002(NOT_PROJECT_MEMBER)가 그대로 새어 나간다.
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_NOT_FOUND);
    }

    @Test
    @DisplayName("섹션이 없으면 역할 조회 전에 SECTION_NOT_FOUND를 던진다")
    void requireOwnedSection_rejectsMissingSection() {
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionAccessGuard.requireOwnedSection(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_NOT_FOUND);
        verifyNoInteractions(projectAccessGuard);
    }

    @Test
    @DisplayName("비멤버는 SECTION_NOT_FOUND 로 숨긴다 (존재 숨김 — CLAUDE.md §5.6)")
    void requireParticipantSection_hidesNonMemberAsNotFound() {
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectAccessGuard.requireParticipant(PROJECT_ID, USER_ID))
                .willThrow(new BusinessException(ErrorCode.NOT_PROJECT_MEMBER));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionAccessGuard.requireParticipantSection(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SECTION_NOT_FOUND);
    }

    @Test
    @DisplayName("멤버지만 역할이 부족하면 FORBIDDEN 을 그대로 전파한다 (403 유지)")
    void requireOwnedSection_propagatesForbiddenForWrongRole() {
        given(projectSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(projectAccessGuard.requireOwner(PROJECT_ID, USER_ID))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sectionAccessGuard.requireOwnedSection(SECTION_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    private ProjectMember membership(ProjectMemberRole role) {
        return ProjectMember.builder()
                .project(section.getProject())
                .role(role)
                .build();
    }
}
