package com.wevo.backend.ai.service;

import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.ProjectFlowReviewContext;
import com.wevo.backend.ai.context.ProjectFlowReviewContextAssembler;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.exception.AiProviderUnavailableException;
import com.wevo.backend.ai.dto.model.ProjectFlowReviewOutput;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.service.SectionConfirmationQueryService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class ProjectFlowReviewJobHandler implements AiJobHandler {
    private static final Logger log = LoggerFactory.getLogger(ProjectFlowReviewJobHandler.class);
    private static final String CHANGED_HASH = "0".repeat(64);
    private final AiJobService jobService;
    private final AiJobRepository jobRepository;
    private final ProjectAccessGuard accessGuard;
    private final SectionConfirmationQueryService confirmationQueryService;
    private final ProjectFlowReviewContextAssembler assembler;
    private final ObjectProvider<ProjectFlowReviewer> reviewerProvider;
    private final ProjectFlowReviewResultWriter resultWriter;
    private final AiUsageResultLinkService usageLinkService;
    private final AiJobHeartbeatService heartbeatService;

    public ProjectFlowReviewJobHandler(AiJobService jobService, AiJobRepository jobRepository,
                                       ProjectAccessGuard accessGuard,
                                       SectionConfirmationQueryService confirmationQueryService,
                                       ProjectFlowReviewContextAssembler assembler,
                                       ObjectProvider<ProjectFlowReviewer> reviewerProvider,
                                       ProjectFlowReviewResultWriter resultWriter,
                                       AiUsageResultLinkService usageLinkService,
                                       AiJobHeartbeatService heartbeatService) {
        this.jobService = jobService; this.jobRepository = jobRepository; this.accessGuard = accessGuard;
        this.confirmationQueryService = confirmationQueryService; this.assembler = assembler;
        this.reviewerProvider = reviewerProvider; this.resultWriter = resultWriter;
        this.usageLinkService = usageLinkService; this.heartbeatService = heartbeatService;
    }

    @Override public AiFeature feature() { return AiFeature.PROJECT_FLOW_REVIEW; }

    @Override
    public void run(UUID requestId) {
        AiJob job = load(requestId);
        if (job == null || !claim(job)) return;
        try (AiJobHeartbeatService.HeartbeatLease ignored =
                     heartbeatService.start(requestId, feature())) {
            execute(job);
        } catch (Exception exception) {
            safeFail(requestId, exception);
        }
    }

    private void execute(AiJob job) {
        AssembledAiContext<ProjectFlowReviewContext> assembled;
        try {
            assembled = assemble(job);
        } catch (RuntimeException changed) {
            jobService.markStale(job.getRequestId());
            return;
        }
        if (!assembled.snapshot().inputSnapshotHash().equals(job.getInputSnapshotHash())) {
            jobService.markStale(job.getRequestId());
            return;
        }
        ProjectFlowReviewer reviewer = reviewerProvider.getIfAvailable();
        if (reviewer == null) throw new AiProviderUnavailableException();
        ProjectFlowReviewOutput output = reviewer.review(job, assembled.context());
        jobService.succeed(job.getRequestId(), () -> completionHash(job), () -> {
            Long resultId = resultWriter.persist(
                    new ProjectFlowReviewPersistCommand(job, assembled.context(), output));
            usageLinkService.linkSuccessfulInvocations(job.getId(), resultId);
            return resultId;
        });
    }

    private String completionHash(AiJob job) {
        try {
            VerifiedProjectAccess access = access(job);
            confirmationQueryService.lockAllForUpdate(access);
            return assembler.assemble(access).snapshot().inputSnapshotHash();
        } catch (RuntimeException changed) {
            return CHANGED_HASH;
        }
    }

    private AssembledAiContext<ProjectFlowReviewContext> assemble(AiJob job) {
        return assembler.assemble(access(job));
    }

    private VerifiedProjectAccess access(AiJob job) {
        return accessGuard.requireParticipantAccess(job.getProject().getId(), job.getRequestedBy().getId());
    }

    private AiJob load(UUID id) {
        try { return jobRepository.findByRequestIdWithExecutionContext(id).orElse(null); }
        catch (Exception e) { log.warn("전체 흐름 AI 작업 조회 실패 requestId={}, exceptionType={}", id, e.getClass().getSimpleName()); return null; }
    }
    private boolean claim(AiJob job) {
        try { return jobService.start(job.getRequestId(), job.getInputSnapshotHash()).claimed(); }
        catch (Exception e) { log.warn("전체 흐름 AI 작업 시작 실패 requestId={}, exceptionType={}", job.getRequestId(), e.getClass().getSimpleName()); return false; }
    }
    private void safeFail(UUID id, Exception e) {
        try { jobService.fail(id, e); }
        catch (Exception failure) { log.warn("전체 흐름 AI 작업 실패 처리 불가 requestId={}, exceptionType={}", id, failure.getClass().getSimpleName()); }
    }
}
