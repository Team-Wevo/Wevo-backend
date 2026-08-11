package com.wevo.backend.ai.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.review.service.ReviewIntentComparisonStateService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class ReviewIntentComparisonJobHandlerTest {

    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final long SUBMISSION_ID = 33L;
    private static final String HASH = "a".repeat(64);

    @Mock private AiJobService aiJobService;
    @Mock private AiJobRepository jobRepository;
    @Mock private ReviewIntentComparisonStateService stateService;
    @Mock private ObjectProvider<ReviewIntentComparator> comparatorProvider;
    @Mock private ReviewIntentComparisonResultWriter resultWriter;
    @Mock private AiUsageResultLinkService usageResultLinkService;
    @Mock private AiInputSnapshotHasher snapshotHasher;
    @Mock private AiProperties properties;
    @Mock private AiErrorClassifier errorClassifier;
    @Mock private AiJobHeartbeatService heartbeatService;
    @Mock private AiJob job;

    @Test
    void heartbeatRegistrationFailureDoesNotLeaveTheJobRunning() {
        ReviewIntentComparisonJobHandler handler = new ReviewIntentComparisonJobHandler(
                aiJobService, jobRepository, stateService, comparatorProvider, resultWriter,
                usageResultLinkService, snapshotHasher, properties, errorClassifier,
                heartbeatService);
        given(jobRepository.findByRequestIdWithExecutionContext(REQUEST_ID))
                .willReturn(Optional.of(job));
        given(job.getRequestId()).willReturn(REQUEST_ID);
        given(job.getInputSnapshotHash()).willReturn(HASH);
        given(job.getSourceVersion()).willReturn("review-submission-33-draft-v1");
        given(aiJobService.start(REQUEST_ID, HASH))
                .willReturn(new AiJobStartResult(REQUEST_ID, AiJobStatus.RUNNING, true));
        given(heartbeatService.start(REQUEST_ID, AiFeature.REVIEW_INTENT_COMPARISON))
                .willThrow(new RejectedExecutionException("scheduler shutdown"));
        given(errorClassifier.classify(any(RejectedExecutionException.class)))
                .willReturn(AiErrorType.INTERNAL_ERROR);

        handler.run(REQUEST_ID);

        verify(aiJobService).fail(eq(REQUEST_ID), any(RejectedExecutionException.class));
        verify(stateService).fail(SUBMISSION_ID, "AI999");
        verify(comparatorProvider, never()).getIfAvailable();
    }
}
