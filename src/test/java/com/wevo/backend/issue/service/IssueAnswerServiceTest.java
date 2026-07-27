package com.wevo.backend.issue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.issue.domain.EvidenceRequest;
import com.wevo.backend.issue.domain.Issue;
import com.wevo.backend.issue.domain.IssueAnswer;
import com.wevo.backend.issue.domain.IssueStatus;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.domain.SynthesisSet;
import com.wevo.backend.issue.dto.request.IssueAnswerRequest;
import com.wevo.backend.issue.dto.response.IssueAnswerResponse;
import com.wevo.backend.issue.repository.EvidenceRequestRepository;
import com.wevo.backend.issue.repository.IssueAnswerRepository;
import com.wevo.backend.section.service.SectionSynthesisStateService;
import com.wevo.backend.user.service.UserService;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class IssueAnswerServiceTest {

    private static final Long ISSUE_ID = 10L;
    private static final Long SECTION_ID = 20L;
    private static final Long TARGET_ID = 30L;
    private static final String CONTENT = "시장 규모 통계와 출처를 보충합니다.";

    @Mock private IssueCommandAccessGuard issueCommandAccessGuard;
    @Mock private EvidenceRequestRepository evidenceRequestRepository;
    @Mock private IssueAnswerRepository issueAnswerRepository;
    @Mock private UserService userService;
    @Mock private SectionSynthesisStateService sectionSynthesisStateService;
    @Mock private Issue issue;
    @Mock private EvidenceRequest evidenceRequest;
    @Mock private SynthesisSet synthesisSet;

    private IssueAnswerService service;

    @BeforeEach
    void setUp() {
        service = new IssueAnswerService(
                issueCommandAccessGuard,
                evidenceRequestRepository,
                issueAnswerRepository,
                userService,
                sectionSynthesisStateService);
    }

    @Test
    @DisplayName("지목된 팀원이 GAP 보충 근거를 답변하고 초안 stale 처리를 위임한다")
    void answer_targetUser_persistsAndResolves() {
        givenValidAnswer();
        given(issue.getId()).willReturn(ISSUE_ID);
        given(issue.getSynthesisSet()).willReturn(synthesisSet);
        given(synthesisSet.getProjectSectionId()).willReturn(SECTION_ID);

        IssueAnswerResponse response =
                service.answer(ISSUE_ID, TARGET_ID, new IssueAnswerRequest(CONTENT));

        assertThat(response.issueId()).isEqualTo(ISSUE_ID);
        assertThat(response.answeredAt()).isNotNull();

        ArgumentCaptor<IssueAnswer> captor = ArgumentCaptor.forClass(IssueAnswer.class);
        then(issueAnswerRepository).should().saveAndFlush(captor.capture());
        IssueAnswer saved = captor.getValue();
        assertThat(saved.getIssue()).isSameAs(issue);
        assertThat(saved.getEvidenceRequest()).isSameAs(evidenceRequest);
        assertThat(saved.getAuthorUserId()).isEqualTo(TARGET_ID);
        assertThat(saved.getAuthorNameSnapshot()).isEqualTo("팀원");
        assertThat(saved.getContent()).isEqualTo(CONTENT);
        assertThat(saved.getAnsweredAt()).isEqualTo(response.answeredAt());
        then(issue).should().resolve(saved);
        then(sectionSynthesisStateService).should()
                .markSynthesisStaleIfDraftExists(SECTION_ID);
    }

    @Test
    @DisplayName("본문이 없거나 공백이거나 1000자를 초과하면 저장소 조회 전에 C001이다")
    void answer_invalidContent_returnsC001() {
        assertError(() -> service.answer(ISSUE_ID, TARGET_ID, null), ErrorCode.INVALID_INPUT);
        assertError(
                () -> service.answer(ISSUE_ID, TARGET_ID, new IssueAnswerRequest(null)),
                ErrorCode.INVALID_INPUT);
        assertError(
                () -> service.answer(ISSUE_ID, TARGET_ID, new IssueAnswerRequest("   ")),
                ErrorCode.INVALID_INPUT);
        assertError(
                () -> service.answer(
                        ISSUE_ID, TARGET_ID, new IssueAnswerRequest("가".repeat(1001))),
                ErrorCode.INVALID_INPUT);

        then(issueCommandAccessGuard).should(never())
                .requireCurrentIssueForParticipant(any(), any());
    }

    @Test
    @DisplayName("GAP이 아니거나 이미 해소된 쟁점이면 C003이다")
    void answer_invalidIssueState_returnsC003() {
        givenCurrentIssue();
        given(issue.getType()).willReturn(IssueType.CONFLICT);
        assertError(
                () -> service.answer(ISSUE_ID, TARGET_ID, new IssueAnswerRequest(CONTENT)),
                ErrorCode.CONFLICT);

        given(issue.getType()).willReturn(IssueType.GAP);
        given(issue.getStatus()).willReturn(IssueStatus.RESOLVED);
        assertError(
                () -> service.answer(ISSUE_ID, TARGET_ID, new IssueAnswerRequest(CONTENT)),
                ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("추가 근거 요청이 없으면 C003이다")
    void answer_withoutRequest_returnsC003() {
        givenCurrentGap();
        given(evidenceRequestRepository.findByIssue_Id(ISSUE_ID)).willReturn(Optional.empty());

        assertError(
                () -> service.answer(ISSUE_ID, TARGET_ID, new IssueAnswerRequest(CONTENT)),
                ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("요청에 지목되지 않은 사용자는 A002이다")
    void answer_notTarget_returnsA002() {
        givenCurrentGap();
        given(evidenceRequestRepository.findByIssue_Id(ISSUE_ID))
                .willReturn(Optional.of(evidenceRequest));
        given(evidenceRequest.getTargetUserId()).willReturn(99L);

        assertError(
                () -> service.answer(ISSUE_ID, TARGET_ID, new IssueAnswerRequest(CONTENT)),
                ErrorCode.FORBIDDEN);
        then(issueAnswerRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("이미 답변한 요청이면 C003이다")
    void answer_alreadyAnswered_returnsC003() {
        givenCurrentRequest();
        given(issueAnswerRepository.findByIssue_Id(ISSUE_ID))
                .willReturn(Optional.of(org.mockito.Mockito.mock(IssueAnswer.class)));

        assertError(
                () -> service.answer(ISSUE_ID, TARGET_ID, new IssueAnswerRequest(CONTENT)),
                ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("답변 유니크 제약 경합은 C003으로 변환한다")
    void answer_uniqueConstraintRace_returnsC003() {
        givenValidAnswer();
        given(issueAnswerRepository.saveAndFlush(any(IssueAnswer.class)))
                .willThrow(constraintViolation("uk_issue_answers_evidence_request"));

        assertError(
                () -> service.answer(ISSUE_ID, TARGET_ID, new IssueAnswerRequest(CONTENT)),
                ErrorCode.CONFLICT);
        then(sectionSynthesisStateService).should(never())
                .markSynthesisStaleIfDraftExists(any());
    }

    @Test
    @DisplayName("예상하지 못한 DB 무결성 오류는 내부 오류로 전환한다")
    void answer_unexpectedConstraint_wrapsAsInternalError() {
        givenValidAnswer();
        DataIntegrityViolationException unexpected =
                constraintViolation("fk_issue_answers_author");
        given(issueAnswerRepository.saveAndFlush(any(IssueAnswer.class)))
                .willThrow(unexpected);

        assertThatThrownBy(() ->
                service.answer(ISSUE_ID, TARGET_ID, new IssueAnswerRequest(CONTENT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("예상하지 못한 보충 근거 답변 데이터 무결성 오류입니다.")
                .hasCause(unexpected);
    }

    private void givenValidAnswer() {
        givenCurrentRequest();
        given(issueAnswerRepository.findByIssue_Id(ISSUE_ID)).willReturn(Optional.empty());
        given(userService.getUserName(TARGET_ID)).willReturn("팀원");
        given(evidenceRequest.getIssue()).willReturn(issue);
    }

    private void givenCurrentRequest() {
        givenCurrentGap();
        given(evidenceRequestRepository.findByIssue_Id(ISSUE_ID))
                .willReturn(Optional.of(evidenceRequest));
        given(evidenceRequest.getTargetUserId()).willReturn(TARGET_ID);
    }

    private void givenCurrentGap() {
        givenCurrentIssue();
        given(issue.getType()).willReturn(IssueType.GAP);
        given(issue.getStatus()).willReturn(IssueStatus.PENDING);
    }

    private void givenCurrentIssue() {
        given(issueCommandAccessGuard
                .requireCurrentIssueForParticipant(ISSUE_ID, TARGET_ID))
                .willReturn(issue);
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
