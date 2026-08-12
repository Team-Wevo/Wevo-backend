package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.review.service.ReviewIntentComparisonRecoveryCandidate;
import com.wevo.backend.review.service.ReviewIntentComparisonStateService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewIntentComparisonRecoveryServiceTest {

    private static final LocalDateTime THRESHOLD = LocalDateTime.of(2026, 8, 12, 10, 0);

    @Mock private ReviewIntentComparisonStateService stateService;
    @Mock private AiJobRepository jobRepository;
    @Mock private AiJob job;

    @Test
    void pendingWithoutJobIsFailedAfterGraceThreshold() {
        ReviewIntentComparisonRecoveryService service = service();
        ReviewIntentComparisonRecoveryCandidate candidate =
                new ReviewIntentComparisonRecoveryCandidate(33L, null);
        given(stateService.findPendingRecoveryCandidates(THRESHOLD))
                .willReturn(List.of(candidate));
        given(stateService.failIfPendingWithoutSourceJob(33L, THRESHOLD, "AI999"))
                .willReturn(true);

        assertThat(service.recoverPendingComparisons(THRESHOLD, 20)).isEqualTo(1);

        verify(stateService).failIfPendingWithoutSourceJob(33L, THRESHOLD, "AI999");
        verify(jobRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void heartbeatFailedJobPropagatesSafeFailureCodeToComparison() {
        ReviewIntentComparisonRecoveryService service = service();
        ReviewIntentComparisonRecoveryCandidate candidate =
                new ReviewIntentComparisonRecoveryCandidate(33L, 44L);
        given(stateService.findPendingRecoveryCandidates(THRESHOLD))
                .willReturn(List.of(candidate));
        given(jobRepository.findById(44L)).willReturn(Optional.of(job));
        given(job.getStatus()).willReturn(AiJobStatus.FAILED);
        given(job.getFinalErrorType()).willReturn(AiErrorType.WORKER_HEARTBEAT_TIMEOUT);
        given(stateService.failIfPendingForTerminalJob(33L, 44L, "AI999"))
                .willReturn(true);

        assertThat(service.recoverPendingComparisons(THRESHOLD, 20)).isEqualTo(1);

        verify(stateService).failIfPendingForTerminalJob(33L, 44L, "AI999");
    }

    @Test
    void queuedOrRunningJobKeepsComparisonPending() {
        ReviewIntentComparisonRecoveryService service = service();
        ReviewIntentComparisonRecoveryCandidate candidate =
                new ReviewIntentComparisonRecoveryCandidate(33L, 44L);
        given(stateService.findPendingRecoveryCandidates(THRESHOLD))
                .willReturn(List.of(candidate));
        given(jobRepository.findById(44L)).willReturn(Optional.of(job));
        given(job.getStatus()).willReturn(AiJobStatus.RUNNING);

        assertThat(service.recoverPendingComparisons(THRESHOLD, 20)).isZero();

        verify(stateService, never()).failIfPendingForTerminalJob(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private ReviewIntentComparisonRecoveryService service() {
        return new ReviewIntentComparisonRecoveryService(stateService, jobRepository);
    }
}
