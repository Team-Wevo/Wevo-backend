package com.wevo.backend.issue.service;

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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OWNER가 현재 정리 세트의 GAP 쟁점에 추가 근거를 요청하는 쓰기 경계. (API_SPEC §3.9.2)
 */
@Service
public class EvidenceRequestService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String UNIQUE_REQUEST_CONSTRAINT = "uk_evidence_requests_issue";

    private final IssueRepository issueRepository;
    private final IssueRelatedOpinionRepository issueRelatedOpinionRepository;
    private final EvidenceRequestRepository evidenceRequestRepository;
    private final CurrentSynthesisSetResolver currentSynthesisSetResolver;
    private final SectionAccessGuard sectionAccessGuard;

    public EvidenceRequestService(
            IssueRepository issueRepository,
            IssueRelatedOpinionRepository issueRelatedOpinionRepository,
            EvidenceRequestRepository evidenceRequestRepository,
            CurrentSynthesisSetResolver currentSynthesisSetResolver,
            SectionAccessGuard sectionAccessGuard
    ) {
        this.issueRepository = issueRepository;
        this.issueRelatedOpinionRepository = issueRelatedOpinionRepository;
        this.evidenceRequestRepository = evidenceRequestRepository;
        this.currentSynthesisSetResolver = currentSynthesisSetResolver;
        this.sectionAccessGuard = sectionAccessGuard;
    }

    /**
     * 관련 의견 작성자를 대상으로 쟁점당 한 번만 추가 근거 요청을 저장한다.
     *
     * <p>쟁점 행과 섹션 행을 순서대로 잠가 중복 요청과 정리 세트 대체를 직렬화한다.
     *
     * @throws BusinessException 쟁점 없음·비멤버({@code I001}), OWNER 아님({@code A002}),
     *                           대상 오류({@code C001}), 중복 요청({@code I003}),
     *                           CONFLICT·이전 세트({@code C003})
     */
    @Transactional
    public EvidenceRequestResponse request(
            Long issueId,
            Long actorUserId,
            EvidenceRequestCreateRequest request
    ) {
        validateRequest(request);

        Issue issue = issueRepository.findByIdForUpdate(issueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ISSUE_NOT_FOUND));
        SynthesisSet issueSet = requireSynthesisSet(issue);

        requireOwner(issueSet.getProjectSectionId(), actorUserId);
        requireCurrentSet(issueSet);
        requireGap(issue);
        requireNotRequested(issueId);

        IssueRelatedOpinion target = issueRelatedOpinionRepository
                .findFirstByIssue_IdAndAuthorUserIdOrderBySortOrderAsc(
                        issueId, request.targetUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));

        EvidenceRequest evidenceRequest = EvidenceRequest.builder()
                .issue(issue)
                .requestedByUserId(actorUserId)
                .targetUserId(request.targetUserId())
                .requestedAt(LocalDateTime.now(KST))
                .build();
        save(evidenceRequest);

        return EvidenceRequestResponse.requested(
                issueId,
                target.getAuthorUserId(),
                target.getAuthorNameSnapshot());
    }

    private void validateRequest(EvidenceRequestCreateRequest request) {
        if (request == null || request.targetUserId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private SynthesisSet requireSynthesisSet(Issue issue) {
        if (issue.getSynthesisSet() == null
                || issue.getSynthesisSet().getId() == null
                || issue.getSynthesisSet().getProjectSectionId() == null) {
            throw new IllegalStateException("쟁점의 정리 세트 참조가 유효하지 않습니다.");
        }
        return issue.getSynthesisSet();
    }

    private void requireOwner(Long sectionId, Long actorUserId) {
        try {
            sectionAccessGuard.requireOwnedSectionForUpdate(sectionId, actorUserId);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.SECTION_NOT_FOUND) {
                throw new BusinessException(ErrorCode.ISSUE_NOT_FOUND);
            }
            throw exception;
        }
    }

    private void requireCurrentSet(SynthesisSet issueSet) {
        CurrentSynthesisSetReference current = currentSynthesisSetResolver
                .findCurrent(issueSet.getProjectSectionId())
                .orElseThrow(() -> new IllegalStateException(
                        "쟁점이 속한 섹션의 현재 정리 세트가 없습니다."));
        if (!Objects.equals(current.synthesisSetId(), issueSet.getId())
                || !Objects.equals(current.requestId(), issueSet.getRequestId())) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }

    private void requireGap(Issue issue) {
        if (issue.getType() != IssueType.GAP) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }

    private void requireNotRequested(Long issueId) {
        if (evidenceRequestRepository.findByIssue_Id(issueId).isPresent()) {
            throw new BusinessException(ErrorCode.EVIDENCE_REQUEST_ALREADY_SENT);
        }
    }

    private void save(EvidenceRequest evidenceRequest) {
        try {
            evidenceRequestRepository.saveAndFlush(evidenceRequest);
        } catch (DataIntegrityViolationException exception) {
            if (violatesConstraint(exception, UNIQUE_REQUEST_CONSTRAINT)) {
                throw new BusinessException(ErrorCode.EVIDENCE_REQUEST_ALREADY_SENT);
            }
            throw new IllegalStateException(
                    "예상하지 못한 추가 근거 요청 데이터 무결성 오류입니다.", exception);
        }
    }

    private boolean violatesConstraint(
            DataIntegrityViolationException exception,
            String expectedConstraint
    ) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation) {
                return expectedConstraint.equals(constraintViolation.getConstraintName());
            }
            current = current.getCause();
        }
        return false;
    }
}
