package com.wevo.backend.issue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.issue.domain.Issue;
import com.wevo.backend.issue.domain.IssueDecision;
import com.wevo.backend.issue.domain.IssueOption;
import com.wevo.backend.issue.domain.IssueStatus;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.domain.SynthesisSet;
import com.wevo.backend.issue.dto.request.IssueDecisionRequest;
import com.wevo.backend.issue.dto.response.IssueDecisionResponse;
import com.wevo.backend.issue.repository.IssueDecisionRepository;
import com.wevo.backend.issue.repository.IssueOptionRepository;
import com.wevo.backend.issue.repository.IssueRepository;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.section.domain.ProjectSection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class IssueDecisionServiceTest {

    private static final Long ISSUE_ID = 10L;
    private static final Long SET_ID = 20L;
    private static final Long SECTION_ID = 30L;
    private static final Long OWNER_ID = 40L;
    private static final UUID SET_REQUEST_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String OPTION_TEXT = "대학생 팀";

    @Mock private IssueRepository issueRepository;
    @Mock private IssueOptionRepository issueOptionRepository;
    @Mock private IssueDecisionRepository issueDecisionRepository;
    @Mock private CurrentSynthesisSetResolver currentSynthesisSetResolver;
    @Mock private SectionAccessGuard sectionAccessGuard;
    @Mock private Issue issue;
    @Mock private IssueOption option;
    @Mock private SynthesisSet issueSet;
    @Mock private ProjectSection section;

    private IssueDecisionService service;

    @BeforeEach
    void setUp() {
        service = new IssueDecisionService(
                issueRepository,
                issueOptionRepository,
                issueDecisionRepository,
                currentSynthesisSetResolver,
                sectionAccessGuard);
    }

    @Test
    @DisplayName("현재 CONFLICT의 선택지를 결정으로 저장하고 RESOLVED 응답을 반환한다")
    void decide_selectedOption_resolvesIssue() {
        givenValidPendingConflict();
        given(issue.getId()).willReturn(ISSUE_ID);
        given(issueOptionRepository.findByIssue_IdAndOptionText(ISSUE_ID, OPTION_TEXT))
                .willReturn(Optional.of(option));
        given(option.getIssue()).willReturn(issue);

        IssueDecisionResponse response = service.decide(
                ISSUE_ID, OWNER_ID, new IssueDecisionRequest(OPTION_TEXT, null));

        ArgumentCaptor<IssueDecision> decisionCaptor = ArgumentCaptor.forClass(IssueDecision.class);
        verify(issueDecisionRepository).saveAndFlush(decisionCaptor.capture());
        IssueDecision decision = decisionCaptor.getValue();
        assertThat(decision.getIssue()).isSameAs(issue);
        assertThat(decision.getDecidedByUserId()).isEqualTo(OWNER_ID);
        assertThat(decision.getSelectedOption()).isSameAs(option);
        assertThat(decision.getCustomInput()).isNull();
        assertThat(decision.getDecidedAt()).isNotNull();
        verify(issue).resolve(decision);
        assertThat(response).isEqualTo(new IssueDecisionResponse(ISSUE_ID, IssueStatus.RESOLVED));
    }

    @Test
    @DisplayName("직접 입력 결정을 저장하고 쟁점을 해소한다")
    void decide_customInput_resolvesIssue() {
        givenValidPendingConflict();
        String customInput = "가 ".repeat(IssueDecision.MAX_CUSTOM_INPUT_LENGTH);

        IssueDecisionResponse response = service.decide(
                ISSUE_ID, OWNER_ID, new IssueDecisionRequest(null, customInput));

        ArgumentCaptor<IssueDecision> decisionCaptor = ArgumentCaptor.forClass(IssueDecision.class);
        verify(issueDecisionRepository).saveAndFlush(decisionCaptor.capture());
        IssueDecision decision = decisionCaptor.getValue();
        assertThat(decision.getSelectedOption()).isNull();
        assertThat(decision.getCustomInput()).isEqualTo(customInput);
        verify(issue).resolve(decision);
        assertThat(response.status()).isEqualTo(IssueStatus.RESOLVED);
        verify(issueOptionRepository, never())
                .findByIssue_IdAndOptionText(any(), any());
    }

    @Test
    @DisplayName("직접 입력의 비공백 문자가 200자를 초과하면 저장소 조회 전에 400 C001이다")
    void decide_customInputOverNonWhitespaceLimit_returnsC001() {
        String tooLong = "가 ".repeat(IssueDecision.MAX_CUSTOM_INPUT_LENGTH + 1);

        assertError(
                () -> service.decide(
                        ISSUE_ID, OWNER_ID, new IssueDecisionRequest(null, tooLong)),
                ErrorCode.INVALID_INPUT);

        verify(issueRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("쟁점이 없으면 404 I001이다")
    void decide_missingIssue_returnsI001() {
        given(issueRepository.findByIdForUpdate(ISSUE_ID)).willReturn(Optional.empty());

        assertError(
                () -> service.decide(
                        ISSUE_ID, OWNER_ID, new IssueDecisionRequest(null, "직접 결정")),
                ErrorCode.ISSUE_NOT_FOUND);

        verify(sectionAccessGuard, never()).requireOwnedSectionForUpdate(any(), any());
    }

    @Test
    @DisplayName("비멤버의 섹션 접근 실패는 쟁점 존재를 숨기는 404 I001로 변환한다")
    void decide_nonMember_returnsI001() {
        givenIssueAndSet();
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, OWNER_ID))
                .willThrow(new BusinessException(ErrorCode.SECTION_NOT_FOUND));

        assertError(
                () -> service.decide(
                        ISSUE_ID, OWNER_ID, new IssueDecisionRequest(null, "직접 결정")),
                ErrorCode.ISSUE_NOT_FOUND);
    }

    @Test
    @DisplayName("멤버지만 OWNER가 아니면 403 A002를 유지한다")
    void decide_member_returnsA002() {
        givenIssueAndSet();
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, OWNER_ID))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertError(
                () -> service.decide(
                        ISSUE_ID, OWNER_ID, new IssueDecisionRequest(null, "직접 결정")),
                ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("이전 정리 세트로 대체된 쟁점은 409 C003이다")
    void decide_supersededIssue_returnsC003() {
        givenIssueAndSet();
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, OWNER_ID))
                .willReturn(section);
        given(currentSynthesisSetResolver.findCurrent(SECTION_ID))
                .willReturn(Optional.of(new CurrentSynthesisSetReference(
                        UUID.fromString("99999999-9999-9999-9999-999999999999"),
                        99L)));

        assertError(
                () -> service.decide(
                        ISSUE_ID, OWNER_ID, new IssueDecisionRequest(null, "직접 결정")),
                ErrorCode.CONFLICT);

        verify(issueDecisionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("GAP 쟁점에 결정하려 하면 409 C003이다")
    void decide_gap_returnsC003() {
        givenCurrentIssue();
        given(issue.getType()).willReturn(IssueType.GAP);

        assertError(
                () -> service.decide(
                        ISSUE_ID, OWNER_ID, new IssueDecisionRequest(null, "직접 결정")),
                ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("이미 RESOLVED인 쟁점의 재결정은 409 C003이다")
    void decide_resolved_returnsC003() {
        givenCurrentIssue();
        given(issue.getType()).willReturn(IssueType.CONFLICT);
        given(issue.getStatus()).willReturn(IssueStatus.RESOLVED);

        assertError(
                () -> service.decide(
                        ISSUE_ID, OWNER_ID, new IssueDecisionRequest(null, "직접 결정")),
                ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("해당 쟁점에 없는 선택지는 400 C001이다")
    void decide_unknownOption_returnsC001() {
        givenValidPendingConflict();
        given(issue.getId()).willReturn(ISSUE_ID);
        given(issueOptionRepository.findByIssue_IdAndOptionText(ISSUE_ID, OPTION_TEXT))
                .willReturn(Optional.empty());

        assertError(
                () -> service.decide(
                        ISSUE_ID, OWNER_ID, new IssueDecisionRequest(OPTION_TEXT, null)),
                ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("결정 중복 유니크 제약은 409 C003으로 변환한다")
    void decide_uniqueConstraintRace_returnsC003() {
        givenValidPendingConflict();
        given(issueDecisionRepository.saveAndFlush(any(IssueDecision.class)))
                .willThrow(constraintViolation("uk_issue_decisions_issue"));

        assertError(
                () -> service.decide(
                        ISSUE_ID, OWNER_ID, new IssueDecisionRequest(null, "직접 결정")),
                ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("예상하지 못한 DB 무결성 오류는 C003으로 숨기지 않고 내부 오류로 전환한다")
    void decide_unexpectedConstraint_wrapsAsInternalError() {
        givenValidPendingConflict();
        DataIntegrityViolationException unexpected =
                constraintViolation("fk_issue_decisions_decided_by");
        given(issueDecisionRepository.saveAndFlush(any(IssueDecision.class)))
                .willThrow(unexpected);

        assertThatThrownBy(() -> service.decide(
                ISSUE_ID,
                OWNER_ID,
                new IssueDecisionRequest(null, "직접 결정")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("예상하지 못한 쟁점 결정 데이터 무결성 오류입니다.")
                .hasCause(unexpected);
    }

    @Test
    @DisplayName("두 입력을 모두 지정하면 저장소 조회 전에 400 C001이다")
    void decide_bothChoices_returnsC001() {
        assertError(
                () -> service.decide(
                        ISSUE_ID, OWNER_ID, new IssueDecisionRequest(OPTION_TEXT, "직접 결정")),
                ErrorCode.INVALID_INPUT);

        verify(issueRepository, never()).findByIdForUpdate(any());
    }

    private void givenValidPendingConflict() {
        givenCurrentIssue();
        given(issue.getType()).willReturn(IssueType.CONFLICT);
        given(issue.getStatus()).willReturn(IssueStatus.PENDING);
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

    private DataIntegrityViolationException constraintViolation(String constraintName) {
        ConstraintViolationException cause = new ConstraintViolationException(
                "constraint violation",
                new SQLException("constraint violation"),
                constraintName);
        return new DataIntegrityViolationException("constraint violation", cause);
    }
}
