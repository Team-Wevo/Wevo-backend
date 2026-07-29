package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiJobDispatchProperties;
import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.DraftReviewContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.dto.model.DraftReviewOutput;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.service.SectionPrecheckStateService;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 사전 검토 작업을 실행하고 자기/상위 snapshot 재대조 후 결과를 원자 반영한다. */
@Component
public class DraftReviewJobHandler implements AiJobHandler {

    private static final Logger log = LoggerFactory.getLogger(DraftReviewJobHandler.class);

    private final AiJobService aiJobService;
    private final AiJobRepository aiJobRepository;
    private final ProjectAccessGuard projectAccessGuard;
    private final AiContextAssembler contextAssembler;
    private final ObjectProvider<SectionDraftReviewer> reviewerProvider;
    private final PrecheckResultWriter resultWriter;
    private final AiUsageResultLinkService usageResultLinkService;
    private final SectionPrecheckStateService precheckStateService;
    private final ScheduledExecutorService heartbeatScheduler;
    private final long heartbeatMillis;

    public DraftReviewJobHandler(
            AiJobService aiJobService,
            AiJobRepository aiJobRepository,
            ProjectAccessGuard projectAccessGuard,
            AiContextAssembler contextAssembler,
            ObjectProvider<SectionDraftReviewer> reviewerProvider,
            PrecheckResultWriter resultWriter,
            AiUsageResultLinkService usageResultLinkService,
            SectionPrecheckStateService precheckStateService,
            ScheduledExecutorService aiHeartbeatScheduler,
            AiJobDispatchProperties properties
    ) {
        this.aiJobService = aiJobService;
        this.aiJobRepository = aiJobRepository;
        this.projectAccessGuard = projectAccessGuard;
        this.contextAssembler = contextAssembler;
        this.reviewerProvider = reviewerProvider;
        this.resultWriter = resultWriter;
        this.usageResultLinkService = usageResultLinkService;
        this.precheckStateService = precheckStateService;
        this.heartbeatScheduler = aiHeartbeatScheduler;
        this.heartbeatMillis = Math.max(1, properties.heartbeatInterval().toMillis());
    }

    @Override
    public AiFeature feature() {
        return AiFeature.DRAFT_REVIEW;
    }

    @Override
    public void run(UUID requestId) {
        AiJob job = loadJob(requestId);
        if (job == null || !claim(requestId, job)) {
            return;
        }
        ScheduledFuture<?> beat = null;
        try {
            beat = heartbeatScheduler.scheduleAtFixedRate(
                    () -> heartbeat(requestId),
                    heartbeatMillis,
                    heartbeatMillis,
                    TimeUnit.MILLISECONDS
            );
            execute(job);
        } catch (Exception exception) {
            safeFail(requestId, exception);
        } finally {
            if (beat != null) {
                beat.cancel(false);
            }
        }
    }

    private void execute(AiJob job) {
        Long sectionId = job.getProjectSection().getId();
        VerifiedProjectAccess access = access(job);
        AssembledAiContext<DraftReviewContext> assembled =
                contextAssembler.assembleDraftReview(access, sectionId);
        if (!assembled.snapshot().inputSnapshotHash().equals(job.getInputSnapshotHash())) {
            aiJobService.markStale(job.getRequestId());
            return;
        }

        SectionDraftReviewer reviewer = reviewerProvider.getIfAvailable();
        if (reviewer == null) {
            throw new IllegalStateException("AI provider가 구성되지 않아 사전 검토를 실행할 수 없습니다.");
        }
        DraftReviewOutput output = reviewer.review(job, assembled.context());

        aiJobService.succeed(job.getRequestId(), () -> {
            VerifiedProjectAccess currentAccess = access(job);
            precheckStateService.lockInputSections(currentAccess, sectionId);
            return contextAssembler.assembleDraftReview(currentAccess, sectionId)
                    .snapshot().inputSnapshotHash();
        }, () -> {
            Long resultId = resultWriter.persist(
                    job.getRequestId(), assembled.context(), output);
            usageResultLinkService.linkSuccessfulInvocations(job.getId(), resultId);
            return resultId;
        });
    }

    private VerifiedProjectAccess access(AiJob job) {
        return projectAccessGuard.requireParticipantAccess(
                job.getProject().getId(),
                job.getRequestedBy().getId()
        );
    }

    private AiJob loadJob(UUID requestId) {
        try {
            return aiJobRepository.findByRequestIdWithExecutionContext(requestId).orElse(null);
        } catch (Exception exception) {
            log.warn("AI 사전 검토 작업 조회 실패, 다음 폴링에서 재시도 requestId={}, exceptionType={}",
                    requestId, exception.getClass().getSimpleName());
            return null;
        }
    }

    private boolean claim(UUID requestId, AiJob job) {
        try {
            return aiJobService.start(requestId, job.getInputSnapshotHash()).claimed();
        } catch (Exception exception) {
            log.warn("AI 사전 검토 작업 시작 실패, 다음 폴링에서 재시도 requestId={}, exceptionType={}",
                    requestId, exception.getClass().getSimpleName());
            return false;
        }
    }

    private void safeFail(UUID requestId, Exception cause) {
        try {
            aiJobService.fail(requestId, cause);
        } catch (Exception failException) {
            log.warn("AI 사전 검토 작업 실패 처리 불가 requestId={}, exceptionType={}",
                    requestId, failException.getClass().getSimpleName());
        }
    }

    private void heartbeat(UUID requestId) {
        try {
            aiJobService.heartbeat(requestId);
        } catch (Exception exception) {
            log.debug("AI 사전 검토 작업 heartbeat 갱신 실패 requestId={}", requestId);
        }
    }
}
