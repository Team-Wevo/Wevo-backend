package com.wevo.backend.issue.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.validation.TextLengthPolicy;
import com.wevo.backend.issue.domain.Issue;
import com.wevo.backend.issue.domain.IssueDecision;
import com.wevo.backend.issue.domain.IssueOption;
import com.wevo.backend.issue.domain.IssueStatus;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.dto.request.IssueDecisionRequest;
import com.wevo.backend.issue.dto.response.IssueDecisionResponse;
import com.wevo.backend.issue.repository.IssueDecisionRepository;
import com.wevo.backend.issue.repository.IssueOptionRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OWNER가 현재 정리 세트의 CONFLICT 쟁점을 결정하는 쓰기 경계. (API_SPEC §3.9.1)
 */
@Service
public class IssueDecisionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String UNIQUE_DECISION_CONSTRAINT = "uk_issue_decisions_issue";

    private final IssueCommandAccessGuard issueCommandAccessGuard;
    private final IssueOptionRepository issueOptionRepository;
    private final IssueDecisionRepository issueDecisionRepository;

    public IssueDecisionService(
            IssueCommandAccessGuard issueCommandAccessGuard,
            IssueOptionRepository issueOptionRepository,
            IssueDecisionRepository issueDecisionRepository
    ) {
        this.issueCommandAccessGuard = issueCommandAccessGuard;
        this.issueOptionRepository = issueOptionRepository;
        this.issueDecisionRepository = issueDecisionRepository;
    }

    /**
     * 선택지 또는 직접 입력으로 쟁점을 결정하고 상태를 {@code RESOLVED}로 변경한다.
     *
     * <p>쟁점 행을 먼저 잠그고, 이어서 섹션 행을 잠가 같은 쟁점의 중복 결정과 정리 세트 대체가
     * 결정 도중 끼어드는 것을 막는다. AI 정리 결과 반영도 같은 섹션 행 잠금을 사용한다.
     *
     * @throws BusinessException 쟁점 없음·비멤버({@code I001}), OWNER 아님({@code A002}),
     *                           잘못된 입력({@code C001}), GAP·이미 해소·이전 세트({@code C003})
     */
    @Transactional
    public IssueDecisionResponse decide(
            Long issueId,
            Long actorUserId,
            IssueDecisionRequest request
    ) {
        validateRequest(request);

        Issue issue =
                issueCommandAccessGuard.requireCurrentIssueForOwner(issueId, actorUserId);
        requirePendingConflict(issue);

        IssueDecision decision = createDecision(issue, actorUserId, request);
        try {
            issueDecisionRepository.saveAndFlush(decision);
            issue.resolve(decision);
        } catch (DataIntegrityViolationException exception) {
            if (IssueConstraintViolationMatcher.matches(
                    exception, UNIQUE_DECISION_CONSTRAINT)) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
            throw new IllegalStateException(
                    "예상하지 못한 쟁점 결정 데이터 무결성 오류입니다.", exception);
        }

        return IssueDecisionResponse.resolved(issueId);
    }

    private void validateRequest(IssueDecisionRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        boolean hasSelectedOption = hasText(request.selectedOption());
        boolean hasCustomInput = hasText(request.customInput());
        if (hasSelectedOption == hasCustomInput
                || hasCustomInput
                && TextLengthPolicy.exceedsNonWhitespaceLimit(
                        request.customInput(), IssueDecision.MAX_CUSTOM_INPUT_LENGTH)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void requirePendingConflict(Issue issue) {
        if (issue.getType() != IssueType.CONFLICT || issue.getStatus() != IssueStatus.PENDING) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }

    private IssueDecision createDecision(
            Issue issue,
            Long actorUserId,
            IssueDecisionRequest request
    ) {
        if (hasText(request.selectedOption())) {
            IssueOption option = issueOptionRepository
                    .findByIssue_IdAndOptionText(issue.getId(), request.selectedOption())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
            return IssueDecision.select(issue, actorUserId, option, LocalDateTime.now(KST));
        }
        return IssueDecision.custom(
                issue, actorUserId, request.customInput(), LocalDateTime.now(KST));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
