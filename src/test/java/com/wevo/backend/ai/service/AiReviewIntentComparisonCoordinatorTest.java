package com.wevo.backend.ai.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.review.domain.ReviewLink;
import com.wevo.backend.review.domain.ReviewSubmission;
import com.wevo.backend.review.service.ReviewIntentComparisonStateService;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiReviewIntentComparisonCoordinatorTest {

    @Mock private ReviewIntentComparisonStateService stateService;
    @Mock private AiInputSnapshotHasher snapshotHasher;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private ReviewSubmission submission;
    @Mock private ReviewLink link;

    private AiReviewIntentComparisonCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new AiReviewIntentComparisonCoordinator(
                stateService, snapshotHasher, properties(), publisher);
        given(submission.getId()).willReturn(33L);
        given(submission.getReviewLink()).willReturn(link);
    }

    @Test
    void missingSummaryIsExplicitlyNotAvailableAndDoesNotQueueProviderWork() {
        given(submission.getSummary()).willReturn(null);
        given(link.getAuthorIntentSnapshot()).willReturn("확정 의도다.");

        coordinator.prepare(submission);

        verify(stateService).prepareNotAvailable(submission);
        verify(stateService, never()).preparePending(any(), any(), any(), any(), any(), any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void comparableSubmissionStoresHashesAndPublishesAfterCommitWork() {
        given(submission.getSummary()).willReturn("검토자가 이해한 문장이다.");
        given(link.getAuthorIntentSnapshot()).willReturn("확정 의도다.");
        given(snapshotHasher.hashCanonical("확정 의도다.")).willReturn("a".repeat(64));
        given(snapshotHasher.hashCanonical("검토자가 이해한 문장이다.")).willReturn("b".repeat(64));

        coordinator.prepare(submission);

        verify(stateService).preparePending(
                eq(submission),
                eq("a".repeat(64)),
                eq("b".repeat(64)),
                eq(ReviewIntentComparisonContract.PROMPT_VERSION),
                eq(ReviewIntentComparisonContract.SCHEMA_VERSION),
                eq("model"));
        verify(publisher).publishEvent(new ReviewIntentComparisonRequestedEvent(33L));
    }

    private AiProperties properties() {
        return new AiProperties(
                "none",
                new AiProperties.ModelOptions(
                        "model",
                        Duration.ofSeconds(1),
                        10_000,
                        1_000,
                        20_000,
                        1_000,
                        AiProperties.ModelOptions.CONSERVATIVE_CHAR_V1,
                        AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1,
                        0,
                        Duration.ZERO,
                        Duration.ZERO),
                Map.of(),
                null);
    }
}
