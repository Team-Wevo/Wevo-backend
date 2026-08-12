package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.review.service.ReviewIntentComparisonRecoveryCandidate;
import com.wevo.backend.review.service.ReviewIntentComparisonStateService;
import java.time.LocalDateTime;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 이벤트 유실 또는 worker 중단 뒤 남은 검토 의도 비교 PENDING 상태를 종결한다. */
@Service
public class ReviewIntentComparisonRecoveryService {

    private static final Logger log =
            LoggerFactory.getLogger(ReviewIntentComparisonRecoveryService.class);
    private static final Set<AiJobStatus> TERMINAL_STATUSES = Set.of(
            AiJobStatus.SUCCEEDED,
            AiJobStatus.FAILED,
            AiJobStatus.CANCELLED,
            AiJobStatus.STALE);

    private final ReviewIntentComparisonStateService stateService;
    private final AiJobRepository jobRepository;

    public ReviewIntentComparisonRecoveryService(
            ReviewIntentComparisonStateService stateService,
            AiJobRepository jobRepository
    ) {
        this.stateService = stateService;
        this.jobRepository = jobRepository;
    }

    public int recoverPendingComparisons(LocalDateTime threshold, int limit) {
        if (threshold == null || limit <= 0) {
            throw new IllegalArgumentException("비교 회수 조건이 유효하지 않습니다.");
        }
        int recovered = 0;
        for (ReviewIntentComparisonRecoveryCandidate candidate
                : stateService.findPendingRecoveryCandidates(threshold)) {
            if (recovered >= limit) {
                break;
            }
            try {
                if (recover(candidate, threshold)) {
                    recovered++;
                    log.warn("검토 의도 비교 PENDING 회수 submissionId={} sourceAiJobId={}",
                            candidate.submissionId(), candidate.sourceAiJobId());
                }
            } catch (RuntimeException exception) {
                log.warn("검토 의도 비교 PENDING 회수 실패·건너뜀 submissionId={}, exceptionType={}",
                        candidate.submissionId(), exception.getClass().getSimpleName());
            }
        }
        return recovered;
    }

    private boolean recover(
            ReviewIntentComparisonRecoveryCandidate candidate,
            LocalDateTime threshold
    ) {
        if (candidate.sourceAiJobId() == null) {
            return stateService.failIfPendingWithoutSourceJob(
                    candidate.submissionId(),
                    threshold,
                    ErrorCode.AI_PROVIDER_ERROR.getCode());
        }
        AiJob job = jobRepository.findById(candidate.sourceAiJobId()).orElse(null);
        if (job == null || !TERMINAL_STATUSES.contains(job.getStatus())) {
            return false;
        }
        AiErrorType errorType = job.getFinalErrorType();
        String failureCode = errorType == null
                ? ErrorCode.AI_PROVIDER_ERROR.getCode()
                : errorType.toErrorCode().getCode();
        return stateService.failIfPendingForTerminalJob(
                candidate.submissionId(), candidate.sourceAiJobId(), failureCode);
    }
}
