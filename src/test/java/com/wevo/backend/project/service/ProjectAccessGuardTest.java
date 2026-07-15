package com.wevo.backend.project.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.repository.ProjectMemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProjectAccessGuardTest {

    private static final Long PROJECT_ID = 10L;
    private static final Long USER_ID = 1L;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @InjectMocks
    private ProjectAccessGuard projectAccessGuard;

    @Test
    @DisplayName("OWNER와 MEMBER는 모두 프로젝트 참여자 검사를 통과한다")
    void requireParticipant_acceptsAnyProjectRole() {
        ProjectMember participant = member(ProjectMemberRole.MEMBER);
        given(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .willReturn(Optional.of(participant));

        ProjectMember result = projectAccessGuard.requireParticipant(PROJECT_ID, USER_ID);

        assertThat(result).isSameAs(participant);
    }

    @Test
    @DisplayName("프로젝트 참여자가 아니면 NOT_PROJECT_MEMBER를 던진다")
    void requireParticipant_rejectsNonParticipant() {
        given(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .willReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> projectAccessGuard.requireParticipant(PROJECT_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_PROJECT_MEMBER);
    }

    @Test
    @DisplayName("OWNER는 팀장 검사를 통과한다")
    void requireOwner_acceptsOwner() {
        ProjectMember owner = member(ProjectMemberRole.OWNER);
        given(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .willReturn(Optional.of(owner));

        assertThat(projectAccessGuard.requireOwner(PROJECT_ID, USER_ID)).isSameAs(owner);
    }

    @Test
    @DisplayName("MEMBER는 팀장 검사를 통과하지 못한다")
    void requireOwner_rejectsMember() {
        given(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .willReturn(Optional.of(member(ProjectMemberRole.MEMBER)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> projectAccessGuard.requireOwner(PROJECT_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("MEMBER는 팀원 검사를 통과한다")
    void requireMember_acceptsMember() {
        ProjectMember member = member(ProjectMemberRole.MEMBER);
        given(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .willReturn(Optional.of(member));

        assertThat(projectAccessGuard.requireMember(PROJECT_ID, USER_ID)).isSameAs(member);
    }

    @Test
    @DisplayName("OWNER는 MEMBER 전용 검사를 통과하지 못한다")
    void requireMember_rejectsOwner() {
        given(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .willReturn(Optional.of(member(ProjectMemberRole.OWNER)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> projectAccessGuard.requireMember(PROJECT_ID, USER_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    private ProjectMember member(ProjectMemberRole role) {
        return ProjectMember.builder().role(role).build();
    }
}
