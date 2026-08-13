package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.review.domain.ReviewSubmission;
import com.wevo.backend.review.service.ReviewIntentComparisonCoordinator;
import com.wevo.backend.review.service.ReviewIntentComparisonStateService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 비교 준비 트랜잭션에는 상태만 저장하고 실제 AI job 생성은 그 커밋 이후 이벤트로 넘긴다.
 *
 * <p>이 트랜잭션은 제출 트랜잭션이 아니라 <b>제출 커밋 이후</b>에 열리는 별도 트랜잭션이므로
 * ({@code ReviewIntentComparisonPreparer}), 여기서 나는 실패는 저장된 제출을 되돌리지 않는다.
 */
@Component
public class AiReviewIntentComparisonCoordinator
        implements ReviewIntentComparisonCoordinator {

    private final ReviewIntentComparisonStateService stateService;
    private final AiInputSnapshotHasher snapshotHasher;
    private final AiProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public AiReviewIntentComparisonCoordinator(
            ReviewIntentComparisonStateService stateService,
            AiInputSnapshotHasher snapshotHasher,
            AiProperties properties,
            ApplicationEventPublisher eventPublisher
    ) {
        this.stateService = stateService;
        this.snapshotHasher = snapshotHasher;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void prepare(ReviewSubmission submission) {
        String summary = submission.getSummary();
        String intent = submission.getReviewLink().getAuthorIntentSnapshot();
        if (summary == null || summary.isBlank() || intent == null || intent.isBlank()) {
            stateService.prepareNotAvailable(submission);
            return;
        }
        AiProperties.ModelOptions options =
                properties.optionsFor(AiFeature.REVIEW_INTENT_COMPARISON);
        stateService.preparePending(
                submission,
                snapshotHasher.hashCanonical(intent),
                snapshotHasher.hashCanonical(summary),
                ReviewIntentComparisonContract.PROMPT_VERSION,
                ReviewIntentComparisonContract.SCHEMA_VERSION,
                options.model());
        eventPublisher.publishEvent(
                new ReviewIntentComparisonRequestedEvent(submission.getId()));
    }
}
