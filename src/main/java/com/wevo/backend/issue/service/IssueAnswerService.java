package com.wevo.backend.issue.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.issue.domain.EvidenceRequest;
import com.wevo.backend.issue.domain.Issue;
import com.wevo.backend.issue.domain.IssueAnswer;
import com.wevo.backend.issue.domain.IssueStatus;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.dto.request.IssueAnswerRequest;
import com.wevo.backend.issue.dto.response.IssueAnswerResponse;
import com.wevo.backend.issue.repository.EvidenceRequestRepository;
import com.wevo.backend.issue.repository.IssueAnswerRepository;
import com.wevo.backend.section.service.SectionSynthesisStateService;
import com.wevo.backend.user.service.UserService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지목된 팀원이 현재 정리 세트의 GAP 쟁점에 보충 근거를 답변하는 쓰기 경계.
 * (API_SPEC §3.9.3)
 */
@Service
public class IssueAnswerService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String UNIQUE_ISSUE_CONSTRAINT = "uk_issue_answers_issue";
    private static final String UNIQUE_REQUEST_CONSTRAINT =
            "uk_issue_answers_evidence_request";

    private final IssueCommandAccessGuard issueCommandAccessGuard;
    private final EvidenceRequestRepository evidenceRequestRepository;
    private final IssueAnswerRepository issueAnswerRepository;
    private final UserService userService;
    private final SectionSynthesisStateService sectionSynthesisStateService;

    public IssueAnswerService(
            IssueCommandAccessGuard issueCommandAccessGuard,
            EvidenceRequestRepository evidenceRequestRepository,
            IssueAnswerRepository issueAnswerRepository,
            UserService userService,
            SectionSynthesisStateService sectionSynthesisStateService
    ) {
        this.issueCommandAccessGuard = issueCommandAccessGuard;
        this.evidenceRequestRepository = evidenceRequestRepository;
        this.issueAnswerRepository = issueAnswerRepository;
        this.userService = userService;
        this.sectionSynthesisStateService = sectionSynthesisStateService;
    }

    /**
     * 보충 근거 답변을 한 번만 저장하고, 이미 초안이 있으면 재정리 필요 상태로 표시한다.
     *
     * <p>일반 의견 수집 게이트와 별개 채널이므로 섹션 상태는 제한하지 않는다.
     * 쟁점 행과 섹션 행을 순서대로 잠가 중복 답변과 정리 세트 대체를 직렬화한다.
     *
     * @throws BusinessException 쟁점 없음·비멤버({@code I001}), 지목 대상 아님({@code A002}),
     *                           요청 없음·중복 답변·이전 세트({@code C003}),
     *                           잘못된 본문({@code C001})
     */
    @Transactional
    public IssueAnswerResponse answer(
            Long issueId,
            Long actorUserId,
            IssueAnswerRequest request
    ) {
        validateRequest(request);

        Issue issue = issueCommandAccessGuard
                .requireCurrentIssueForParticipant(issueId, actorUserId);
        requirePendingGap(issue);

        EvidenceRequest evidenceRequest = evidenceRequestRepository
                .findByIssue_Id(issueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT));
        requireTarget(evidenceRequest, actorUserId);
        requireNotAnswered(issueId);

        LocalDateTime answeredAt = LocalDateTime.now(KST).truncatedTo(ChronoUnit.MICROS);
        IssueAnswer answer = IssueAnswer.builder()
                .evidenceRequest(evidenceRequest)
                .authorUserId(actorUserId)
                .authorNameSnapshot(userService.getUserName(actorUserId))
                .content(request.content())
                .answeredAt(answeredAt)
                .build();
        saveAndResolve(issue, answer);

        sectionSynthesisStateService.markSynthesisStaleIfDraftExists(
                issue.getSynthesisSet().getProjectSectionId());
        return IssueAnswerResponse.from(answer);
    }

    private void validateRequest(IssueAnswerRequest request) {
        if (request == null
                || request.content() == null
                || request.content().isBlank()
                || request.content().length() > IssueAnswer.MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void requirePendingGap(Issue issue) {
        if (issue.getType() != IssueType.GAP || issue.getStatus() != IssueStatus.PENDING) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }

    private void requireTarget(EvidenceRequest evidenceRequest, Long actorUserId) {
        if (!actorUserId.equals(evidenceRequest.getTargetUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireNotAnswered(Long issueId) {
        if (issueAnswerRepository.findByIssue_Id(issueId).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }

    private void saveAndResolve(Issue issue, IssueAnswer answer) {
        try {
            issueAnswerRepository.saveAndFlush(answer);
            issue.resolve(answer);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateAnswer(exception)) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
            throw new IllegalStateException(
                    "예상하지 못한 보충 근거 답변 데이터 무결성 오류입니다.", exception);
        }
    }

    private boolean isDuplicateAnswer(DataIntegrityViolationException exception) {
        return IssueConstraintViolationMatcher.matches(exception, UNIQUE_ISSUE_CONSTRAINT)
                || IssueConstraintViolationMatcher.matches(exception, UNIQUE_REQUEST_CONSTRAINT);
    }
}
