package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiJobDispatchProperties;
import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.ReviewIntentComparisonContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.exception.AiProviderUnavailableException;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.dto.model.ReviewIntentComparisonOutput;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.review.service.ReviewIntentComparisonInput;
import com.wevo.backend.review.service.ReviewIntentComparisonStateService;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class ReviewIntentComparisonJobHandler implements AiJobHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ReviewIntentComparisonJobHandler.class);

    private final AiJobService aiJobService;
    private final AiJobRepository jobRepository;
    private final ReviewIntentComparisonStateService stateService;
    private final ObjectProvider<ReviewIntentComparator> comparatorProvider;
    private final ReviewIntentComparisonResultWriter resultWriter;
    private final AiUsageResultLinkService usageResultLinkService;
    private final AiInputSnapshotHasher snapshotHasher;
    private final AiProperties properties;
    private final AiErrorClassifier errorClassifier;
    private final ScheduledExecutorService heartbeatScheduler;
    private final long heartbeatMillis;

    public ReviewIntentComparisonJobHandler(
            AiJobService aiJobService,
            AiJobRepository jobRepository,
            ReviewIntentComparisonStateService stateService,
            ObjectProvider<ReviewIntentComparator> comparatorProvider,
            ReviewIntentComparisonResultWriter resultWriter,
            AiUsageResultLinkService usageResultLinkService,
            AiInputSnapshotHasher snapshotHasher,
            AiProperties properties,
            AiErrorClassifier errorClassifier,
            ScheduledExecutorService aiHeartbeatScheduler,
            AiJobDispatchProperties dispatchProperties
    ) {
        this.aiJobService = aiJobService;
        this.jobRepository = jobRepository;
        this.stateService = stateService;
        this.comparatorProvider = comparatorProvider;
        this.resultWriter = resultWriter;
        this.usageResultLinkService = usageResultLinkService;
        this.snapshotHasher = snapshotHasher;
        this.properties = properties;
        this.errorClassifier = errorClassifier;
        this.heartbeatScheduler = aiHeartbeatScheduler;
        this.heartbeatMillis = Math.max(1, dispatchProperties.heartbeatInterval().toMillis());
    }

    @Override
    public AiFeature feature() {
        return AiFeature.REVIEW_INTENT_COMPARISON;
    }

    @Override
    public void run(UUID requestId) {
        AiJob job = loadJob(requestId);
        if (job == null) {
            return;
        }
        long submissionId = ReviewIntentComparisonContract.submissionId(job.getSourceVersion());
        AiJobStartResult start = claim(job);
        if (start == null || !start.claimed()) {
            if (start != null && start.status() == AiJobStatus.STALE) {
                stateService.fail(submissionId, ErrorCode.AI_JOB_INPUT_CHANGED.getCode());
            }
            return;
        }
        ScheduledFuture<?> beat = heartbeatScheduler.scheduleAtFixedRate(
                () -> heartbeat(requestId), heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
        try {
            execute(job, submissionId);
        } catch (Exception exception) {
            safeFail(job, submissionId, exception);
        } finally {
            beat.cancel(false);
        }
    }

    private void execute(AiJob job, long submissionId) {
        ReviewIntentComparisonContext context = context(stateService.getInput(submissionId));
        String currentHash = snapshot(context);
        if (!currentHash.equals(job.getInputSnapshotHash())) {
            aiJobService.markStale(job.getRequestId());
            stateService.fail(submissionId, ErrorCode.AI_JOB_INPUT_CHANGED.getCode());
            return;
        }
        ReviewIntentComparator comparator = comparatorProvider.getIfAvailable();
        if (comparator == null) {
            throw new AiProviderUnavailableException();
        }
        ReviewIntentComparisonOutput output = comparator.compare(job, context);
        aiJobService.succeed(job.getRequestId(), () -> {
            ReviewIntentComparisonInput locked = stateService.lockInput(submissionId);
            return snapshot(context(locked));
        }, () -> {
            Long resultId = resultWriter.persist(job, context, output);
            usageResultLinkService.linkSuccessfulInvocations(job.getId(), resultId);
            return resultId;
        });
    }

    private ReviewIntentComparisonContext context(ReviewIntentComparisonInput input) {
        return new ReviewIntentComparisonContext(
                input.submissionId(), input.sectionId(), input.contentVersion(),
                input.authorIntent(), input.reviewerSummary());
    }

    private String snapshot(ReviewIntentComparisonContext context) {
        return snapshotHasher.snapshot(
                context, properties.optionsFor(AiFeature.REVIEW_INTENT_COMPARISON))
                .inputSnapshotHash();
    }

    private AiJob loadJob(UUID requestId) {
        try {
            return jobRepository.findByRequestIdWithExecutionContext(requestId).orElse(null);
        } catch (Exception exception) {
            log.warn("검토 의도 비교 작업 조회 실패 requestId={}, exceptionType={}",
                    requestId, exception.getClass().getSimpleName());
            return null;
        }
    }

    private AiJobStartResult claim(AiJob job) {
        try {
            return aiJobService.start(job.getRequestId(), job.getInputSnapshotHash());
        } catch (Exception exception) {
            log.warn("검토 의도 비교 작업 시작 실패 requestId={}, exceptionType={}",
                    job.getRequestId(), exception.getClass().getSimpleName());
            return null;
        }
    }

    private void safeFail(AiJob job, long submissionId, Exception exception) {
        try {
            aiJobService.fail(job.getRequestId(), exception);
        } finally {
            String code = errorClassifier.classify(exception).toErrorCode().getCode();
            stateService.fail(submissionId, code);
        }
    }

    private void heartbeat(UUID requestId) {
        try {
            aiJobService.heartbeat(requestId);
        } catch (Exception exception) {
            log.debug("검토 의도 비교 작업 heartbeat 갱신 실패 requestId={}", requestId);
        }
    }
}
