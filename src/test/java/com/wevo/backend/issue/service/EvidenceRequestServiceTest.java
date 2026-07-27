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
import com.wevo.backend.issue.domain.IssueRelatedOpinion;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.domain.SynthesisSet;
import com.wevo.backend.issue.dto.request.EvidenceRequestCreateRequest;
import com.wevo.backend.issue.dto.response.EvidenceRequestResponse;
import com.wevo.backend.issue.repository.EvidenceRequestRepository;
import com.wevo.backend.issue.repository.IssueRelatedOpinionRepository;
import com.wevo.backend.issue.repository.IssueRepository;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.section.domain.ProjectSection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
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
class EvidenceRequestServiceTest {

    private static final Long ISSUE_ID = 10L;
    private static final Long SECTION_ID = 20L;
    private static final Long SET_ID = 30L;
    private static final Long OWNER_ID = 40L;
    private static final Long TARGET_ID = 50L;
    private static final UUID SET_REQUEST_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private IssueRepository issueRepository;
    @Mock private IssueRelatedOpinionRepository issueRelatedOpinionRepository;
    @Mock private EvidenceRequestRepository evidenceRequestRepository;
    @Mock private CurrentSynthesisSetResolver currentSynthesisSetResolver;
    @Mock private SectionAccessGuard sectionAccessGuard;
    @Mock private Issue issue;
    @Mock private SynthesisSet issueSet;
    @Mock private ProjectSection section;
    @Mock private IssueRelatedOpinion target;

    private EvidenceRequestService service;

    @BeforeEach
    void setUp() {
        service = new EvidenceRequestService(
                issueRepository,
                issueRelatedOpinionRepository,
                evidenceRequestRepository,
                currentSynthesisSetResolver,
                sectionAccessGuard);
    }

    @Test
    @DisplayName("OWNER가 GAP 관련 의견 작성자에게 추가 근거를 요청한다")
    void request_validTarget_persistsRequest() {
        givenValidRequest();
        given(target.getAuthorUserId()).willReturn(TARGET_ID);
        given(target.getAuthorNameSnapshot()).willReturn("팀원");

        EvidenceRequestResponse response = service.request(
                ISSUE_ID,
                OWNER_ID,
                new EvidenceRequestCreateRequest(TARGET_ID));

        assertThat(response.issueId()).isEqualTo(ISSUE_ID);
        assertThat(response.requestedTo().userId()).isEqualTo(TARGET_ID);
        assertThat(response.requestedTo().name()).isEqualTo("팀원");

        ArgumentCaptor<EvidenceRequest> captor = ArgumentCaptor.forClass(EvidenceRequest.class);
        then(evidenceRequestRepository).should().saveAndFlush(captor.capture());
        EvidenceRequest saved = captor.getValue();
        assertThat(saved.getIssue()).isEqualTo(issue);
        assertThat(saved.getRequestedByUserId()).isEqualTo(OWNER_ID);
        assertThat(saved.getTargetUserId()).isEqualTo(TARGET_ID);
        assertThat(saved.getRequestedAt()).isNotNull();
    }

    @Test
    @DisplayName("targetUserId가 없으면 저장소 조회 전에 400 C001이다")
    void request_missingTarget_returnsC001() {
        assertError(
                () -> service.request(
                        ISSUE_ID, OWNER_ID, new EvidenceRequestCreateRequest(null)),
                ErrorCode.INVALID_INPUT);

        then(issueRepository).should(never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("쟁점이 없으면 404 I001이다")
    void request_missingIssue_returnsI001() {
        given(issueRepository.findByIdForUpdate(ISSUE_ID)).willReturn(Optional.empty());

        assertError(
                () -> service.request(
                        ISSUE_ID, OWNER_ID, new EvidenceRequestCreateRequest(TARGET_ID)),
                ErrorCode.ISSUE_NOT_FOUND);
    }

    @Test
    @DisplayName("비멤버의 섹션 접근 실패는 쟁점 존재를 숨기는 404 I001로 변환한다")
    void request_nonMember_returnsI001() {
        givenIssueAndSet();
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, OWNER_ID))
                .willThrow(new BusinessException(ErrorCode.SECTION_NOT_FOUND));

        assertError(
                () -> service.request(
                        ISSUE_ID, OWNER_ID, new EvidenceRequestCreateRequest(TARGET_ID)),
                ErrorCode.ISSUE_NOT_FOUND);
    }

    @Test
    @DisplayName("멤버지만 OWNER가 아니면 403 A002이다")
    void request_member_returnsA002() {
        givenIssueAndSet();
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, OWNER_ID))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertError(
                () -> service.request(
                        ISSUE_ID, OWNER_ID, new EvidenceRequestCreateRequest(TARGET_ID)),
                ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("이전 정리 세트로 대체된 쟁점은 409 C003이다")
    void request_supersededIssue_returnsC003() {
        givenIssueAndSet();
        given(sectionAccessGuard.requireOwnedSectionForUpdate(SECTION_ID, OWNER_ID))
                .willReturn(section);
        given(currentSynthesisSetResolver.findCurrent(SECTION_ID))
                .willReturn(Optional.of(new CurrentSynthesisSetReference(
                        UUID.fromString("99999999-9999-9999-9999-999999999999"),
                        99L)));

        assertError(
                () -> service.request(
                        ISSUE_ID, OWNER_ID, new EvidenceRequestCreateRequest(TARGET_ID)),
                ErrorCode.CONFLICT);

        then(evidenceRequestRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("CONFLICT 쟁점에 근거를 요청하면 409 C003이다")
    void request_conflictIssue_returnsC003() {
        givenCurrentIssue();
        given(issue.getType()).willReturn(IssueType.CONFLICT);

        assertError(
                () -> service.request(
                        ISSUE_ID, OWNER_ID, new EvidenceRequestCreateRequest(TARGET_ID)),
                ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("이미 근거를 요청한 쟁점은 409 I003이다")
    void request_alreadyRequested_returnsI003() {
        givenCurrentGap();
        given(evidenceRequestRepository.findByIssue_Id(ISSUE_ID))
                .willReturn(Optional.of(org.mockito.Mockito.mock(EvidenceRequest.class)));

        assertError(
                () -> service.request(
                        ISSUE_ID, OWNER_ID, new EvidenceRequestCreateRequest(TARGET_ID)),
                ErrorCode.EVIDENCE_REQUEST_ALREADY_SENT);
    }

    @Test
    @DisplayName("관련 의견 작성자가 아닌 사용자를 지정하면 400 C001이다")
    void request_unrelatedTarget_returnsC001() {
        givenCurrentGap();
        given(evidenceRequestRepository.findByIssue_Id(ISSUE_ID)).willReturn(Optional.empty());
        given(issueRelatedOpinionRepository
                .findFirstByIssue_IdAndAuthorUserIdOrderBySortOrderAsc(ISSUE_ID, TARGET_ID))
                .willReturn(Optional.empty());

        assertError(
                () -> service.request(
                        ISSUE_ID, OWNER_ID, new EvidenceRequestCreateRequest(TARGET_ID)),
                ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("쟁점별 요청 유니크 제약 위반은 409 I003으로 변환한다")
    void request_uniqueConstraintRace_returnsI003() {
        givenValidRequest();
        given(evidenceRequestRepository.saveAndFlush(any(EvidenceRequest.class)))
                .willThrow(constraintViolation("uk_evidence_requests_issue"));

        assertError(
                () -> service.request(
                        ISSUE_ID, OWNER_ID, new EvidenceRequestCreateRequest(TARGET_ID)),
                ErrorCode.EVIDENCE_REQUEST_ALREADY_SENT);
    }

    @Test
    @DisplayName("예상하지 못한 DB 무결성 오류는 I003으로 숨기지 않고 내부 오류로 전환한다")
    void request_unexpectedConstraint_wrapsAsInternalError() {
        givenValidRequest();
        DataIntegrityViolationException unexpected =
                constraintViolation("fk_evidence_requests_target");
        given(evidenceRequestRepository.saveAndFlush(any(EvidenceRequest.class)))
                .willThrow(unexpected);

        assertThatThrownBy(() -> service.request(
                ISSUE_ID,
                OWNER_ID,
                new EvidenceRequestCreateRequest(TARGET_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("예상하지 못한 추가 근거 요청 데이터 무결성 오류입니다.")
                .hasCause(unexpected);
    }

    private void givenValidRequest() {
        givenCurrentGap();
        given(evidenceRequestRepository.findByIssue_Id(ISSUE_ID)).willReturn(Optional.empty());
        given(issueRelatedOpinionRepository
                .findFirstByIssue_IdAndAuthorUserIdOrderBySortOrderAsc(ISSUE_ID, TARGET_ID))
                .willReturn(Optional.of(target));
    }

    private void givenCurrentGap() {
        givenCurrentIssue();
        given(issue.getType()).willReturn(IssueType.GAP);
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
