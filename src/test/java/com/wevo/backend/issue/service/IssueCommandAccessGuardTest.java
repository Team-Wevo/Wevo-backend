package com.wevo.backend.issue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.issue.domain.Issue;
import com.wevo.backend.issue.domain.SynthesisSet;
import com.wevo.backend.issue.repository.IssueRepository;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.section.domain.ProjectSection;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssueCommandAccessGuardTest {

    private static final Long ISSUE_ID = 10L;
    private static final Long SET_ID = 20L;
    private static final Long SECTION_ID = 30L;
    private static final Long OWNER_ID = 40L;
    private static final Long MEMBER_ID = 50L;
    private static final UUID SET_REQUEST_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private IssueRepository issueRepository;
    @Mock private CurrentSynthesisSetResolver currentSynthesisSetResolver;
    @Mock private SectionAccessGuard sectionAccessGuard;
    @Mock private Issue issue;
    @Mock private SynthesisSet issueSet;
    @Mock private ProjectSection section;

    private IssueCommandAccessGuard guard;

    @BeforeEach
    void setUp() {
        guard = new IssueCommandAccessGuard(
                issueRepository,
                currentSynthesisSetResolver,
                sectionAccessGuard);
    }

    @Test
    @DisplayName("쟁점과 섹션을 순서대로 잠그고 OWNER의 현재 세트 쟁점을 반환한다")
    void requireCurrentIssueForOwner_validAccess_returnsLockedIssue() {
        givenCurrentIssue();

        Issue result = guard.requireCurrentIssueForOwner(ISSUE_ID, OWNER_ID);

        assertThat(result).isSameAs(issue);
        InOrder order = inOrder(issueRepository, sectionAccessGuard, currentSynthesisSetResolver);
        order.verify(issueRepository).findByIdForUpdate(ISSUE_ID);
        order.verify(sectionAccessGuard).requireOwnedSectionForUpdate(SECTION_ID, OWNER_ID);
        order.verify(currentSynthesisSetResolver).findCurrent(SECTION_ID);
    }

    @Test
    @DisplayName("쟁점과 섹션을 순서대로 잠그고 참여자의 현재 세트 쟁점을 반환한다")
    void requireCurrentIssueForParticipant_validAccess_returnsLockedIssue() {
        givenIssueAndSet();
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, MEMBER_ID))
                .willReturn(section);
        given(issueSet.getRequestId()).willReturn(SET_REQUEST_ID);
        given(currentSynthesisSetResolver.findCurrent(SECTION_ID))
                .willReturn(Optional.of(
                        new CurrentSynthesisSetReference(SET_REQUEST_ID, SET_ID)));

        Issue result = guard.requireCurrentIssueForParticipant(ISSUE_ID, MEMBER_ID);

        assertThat(result).isSameAs(issue);
        InOrder order = inOrder(issueRepository, sectionAccessGuard, currentSynthesisSetResolver);
        order.verify(issueRepository).findByIdForUpdate(ISSUE_ID);
        order.verify(sectionAccessGuard)
                .requireParticipantSectionForUpdate(SECTION_ID, MEMBER_ID);
        order.verify(currentSynthesisSetResolver).findCurrent(SECTION_ID);
    }

    @Test
    @DisplayName("쟁점이 없으면 404 I001이다")
    void requireCurrentIssueForOwner_missingIssue_returnsI001() {
        given(issueRepository.findByIdForUpdate(ISSUE_ID)).willReturn(Optional.empty());

        assertError(
                () -> guard.requireCurrentIssueForOwner(ISSUE_ID, OWNER_ID),
                ErrorCode.ISSUE_NOT_FOUND);

        verify(sectionAccessGuard, never()).requireOwnedSectionForUpdate(any(), any());
    }

    @Test
    @DisplayName("쟁점의 정리 세트 참조가 유효하지 않으면 내부 오류다")
    void requireCurrentIssueForOwner_invalidSetReference_fails() {
        given(issueRepository.findByIdForUpdate(ISSUE_ID)).willReturn(Optional.of(issue));
        given(issue.getSynthesisSet()).willReturn(null);

        assertThatThrownBy(() -> guard.requireCurrentIssueForOwner(ISSUE_ID, OWNER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("쟁점의 정리 세트 참조가 유효하지 않습니다.");
    }

    @Test
    @DisplayName("비멤버의 섹션 접근 실패는 쟁점 존재를 숨기는 404 I001로 변환한다")
    void requireCurrentIssueForOwner_nonMember_returnsI001() {
        givenIssueAndSet();
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, OWNER_ID))
                .willThrow(new BusinessException(ErrorCode.SECTION_NOT_FOUND));

        assertError(
                () -> guard.requireCurrentIssueForOwner(ISSUE_ID, OWNER_ID),
                ErrorCode.ISSUE_NOT_FOUND);
    }

    @Test
    @DisplayName("비멤버의 참여자 접근 실패도 쟁점 존재를 숨기는 404 I001로 변환한다")
    void requireCurrentIssueForParticipant_nonMember_returnsI001() {
        givenIssueAndSet();
        given(sectionAccessGuard.requireParticipantSectionForUpdate(SECTION_ID, MEMBER_ID))
                .willThrow(new BusinessException(ErrorCode.SECTION_NOT_FOUND));

        assertError(
                () -> guard.requireCurrentIssueForParticipant(ISSUE_ID, MEMBER_ID),
                ErrorCode.ISSUE_NOT_FOUND);
    }

    @Test
    @DisplayName("멤버지만 OWNER가 아니면 403 A002를 유지한다")
    void requireCurrentIssueForOwner_member_returnsA002() {
        givenIssueAndSet();
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, OWNER_ID))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertError(
                () -> guard.requireCurrentIssueForOwner(ISSUE_ID, OWNER_ID),
                ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("성공한 정리 세트가 없으면 내부 무결성 오류다")
    void requireCurrentIssueForOwner_withoutCurrentSet_fails() {
        givenIssueAndSet();
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, OWNER_ID))
                .willReturn(section);
        given(currentSynthesisSetResolver.findCurrent(SECTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireCurrentIssueForOwner(ISSUE_ID, OWNER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("쟁점이 속한 섹션의 현재 정리 세트가 없습니다.");
    }

    @Test
    @DisplayName("현재 세트의 내부 ID가 다르면 409 C003이다")
    void requireCurrentIssueForOwner_differentSetId_returnsC003() {
        givenIssueAndSet();
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, OWNER_ID))
                .willReturn(section);
        given(currentSynthesisSetResolver.findCurrent(SECTION_ID))
                .willReturn(Optional.of(
                        new CurrentSynthesisSetReference(SET_REQUEST_ID, 99L)));

        assertError(
                () -> guard.requireCurrentIssueForOwner(ISSUE_ID, OWNER_ID),
                ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("현재 세트의 requestId가 다르면 409 C003이다")
    void requireCurrentIssueForOwner_differentRequestId_returnsC003() {
        givenIssueAndSet();
        given(issueSet.getRequestId()).willReturn(SET_REQUEST_ID);
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, OWNER_ID))
                .willReturn(section);
        given(currentSynthesisSetResolver.findCurrent(SECTION_ID))
                .willReturn(Optional.of(new CurrentSynthesisSetReference(
                        UUID.fromString("99999999-9999-9999-9999-999999999999"),
                        SET_ID)));

        assertError(
                () -> guard.requireCurrentIssueForOwner(ISSUE_ID, OWNER_ID),
                ErrorCode.CONFLICT);
    }

    private void givenCurrentIssue() {
        givenIssueAndSet();
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, OWNER_ID))
                .willReturn(section);
        given(issueSet.getRequestId()).willReturn(SET_REQUEST_ID);
        given(currentSynthesisSetResolver.findCurrent(SECTION_ID))
                .willReturn(Optional.of(
                        new CurrentSynthesisSetReference(SET_REQUEST_ID, SET_ID)));
    }

    private void givenIssueAndSet() {
        given(issueRepository.findByIdForUpdate(ISSUE_ID)).willReturn(Optional.of(issue));
        given(issue.getSynthesisSet()).willReturn(issueSet);
        given(issueSet.getId()).willReturn(SET_ID);
        given(issueSet.getProjectSectionId()).willReturn(SECTION_ID);
    }

    private void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()).isEqualTo(expected));
    }
}
