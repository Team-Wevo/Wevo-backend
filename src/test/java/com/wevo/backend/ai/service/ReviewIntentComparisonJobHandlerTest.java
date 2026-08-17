package com.wevo.backend.ai.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.AiInputSnapshot;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.dto.model.ReviewIntentComparisonOutput;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.review.domain.ReviewIntentAlignment;
import com.wevo.backend.review.service.ReviewIntentComparisonInput;
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
    @Mock private AiProperties.ModelOptions modelOptions;
    @Mock private AiErrorClassifier errorClassifier;
    @Mock private AiJobHeartbeatService heartbeatService;
    @Mock private ReviewIntentComparator comparator;
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

    @Test
    void completionSnapshotMismatchFailsPendingComparisonAsInputChanged() {
        ReviewIntentComparisonJobHandler handler = new ReviewIntentComparisonJobHandler(
                aiJobService, jobRepository, stateService, comparatorProvider, resultWriter,
                usageResultLinkService, snapshotHasher, properties, errorClassifier,
                heartbeatService);
        ReviewIntentComparisonInput input = new ReviewIntentComparisonInput(
                SUBMISSION_ID, 1L, 2L, 3L, 4, "작성자 의도", "검토자 요약");
        ReviewIntentComparisonOutput output = new ReviewIntentComparisonOutput(
                ReviewIntentAlignment.ALIGNED, "차이가 없습니다.", "검토자 요약");
        given(jobRepository.findByRequestIdWithExecutionContext(REQUEST_ID))
                .willReturn(Optional.of(job));
        given(job.getRequestId()).willReturn(REQUEST_ID);
        given(job.getInputSnapshotHash()).willReturn(HASH);
        given(job.getSourceVersion()).willReturn("review-submission-33-draft-v1");
        given(aiJobService.start(REQUEST_ID, HASH))
                .willReturn(new AiJobStartResult(REQUEST_ID, AiJobStatus.RUNNING, true));
        given(stateService.getInput(SUBMISSION_ID)).willReturn(input);
        given(properties.optionsFor(AiFeature.REVIEW_INTENT_COMPARISON))
                .willReturn(modelOptions);
        given(snapshotHasher.snapshot(any(), eq(modelOptions)))
                .willReturn(new AiInputSnapshot(new byte[]{1}, HASH));
        given(comparatorProvider.getIfAvailable()).willReturn(comparator);
        given(comparator.compare(any(), any())).willReturn(output);
        given(aiJobService.succeed(eq(REQUEST_ID), any(), any()))
                .willReturn(new AiJobCompletionResult(
                        REQUEST_ID, AiJobStatus.STALE, null, false));

        handler.run(REQUEST_ID);

        verify(stateService).fail(SUBMISSION_ID, "AI024");
        verify(resultWriter, never()).persist(any(), any(), any());
        verify(usageResultLinkService, never()).linkSuccessfulInvocations(any(), any());
    }
}
