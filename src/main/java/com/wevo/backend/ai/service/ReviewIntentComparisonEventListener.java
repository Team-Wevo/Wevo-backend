package com.wevo.backend.ai.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.review.service.ReviewIntentComparisonStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ReviewIntentComparisonEventListener {

    private static final Logger log =
            LoggerFactory.getLogger(ReviewIntentComparisonEventListener.class);

    private final ReviewIntentComparisonRequestService requestService;
    private final ReviewIntentComparisonStateService stateService;

    public ReviewIntentComparisonEventListener(
            ReviewIntentComparisonRequestService requestService,
            ReviewIntentComparisonStateService stateService
    ) {
        this.requestService = requestService;
        this.stateService = stateService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterSubmissionCommitted(ReviewIntentComparisonRequestedEvent event) {
        try {
            requestService.request(event.submissionId());
        } catch (RuntimeException exception) {
            stateService.fail(event.submissionId(), safeErrorCode(exception));
            log.warn("검토 의도 비교 작업 생성 실패 submissionId={}, exceptionType={}",
                    event.submissionId(), exception.getClass().getSimpleName());
        }
    }

    private String safeErrorCode(RuntimeException exception) {
        if (exception instanceof BusinessException businessException
                && businessException.getErrorCode() == ErrorCode.AI_PROVIDER_UNAVAILABLE) {
            return ErrorCode.AI_PROVIDER_UNAVAILABLE.getCode();
        }
        return ErrorCode.AI_PROVIDER_ERROR.getCode();
    }
}
