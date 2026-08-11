package com.wevo.backend.ai.service;

import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.OpinionClusteringContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.exception.AiProviderUnavailableException;
import com.wevo.backend.ai.dto.model.OpinionClusteringOutput;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** snapshot 재대조를 통과한 partition만 짧은 완료 트랜잭션에서 저장한다. */
@Component
public class OpinionClusteringJobHandler implements AiJobHandler {

    private static final Logger log = LoggerFactory.getLogger(OpinionClusteringJobHandler.class);

    private final AiJobService aiJobService;
    private final AiJobRepository jobRepository;
    private final SectionAccessGuard sectionAccessGuard;
    private final ProjectAccessGuard projectAccessGuard;
    private final AiContextAssembler contextAssembler;
    private final ObjectProvider<OpinionClusterer> clustererProvider;
    private final OpinionClusteringResultWriter resultWriter;
    private final AiUsageResultLinkService usageResultLinkService;
    private final AiJobHeartbeatService heartbeatService;

    public OpinionClusteringJobHandler(
            AiJobService aiJobService,
            AiJobRepository jobRepository,
            SectionAccessGuard sectionAccessGuard,
            ProjectAccessGuard projectAccessGuard,
            AiContextAssembler contextAssembler,
            ObjectProvider<OpinionClusterer> clustererProvider,
            OpinionClusteringResultWriter resultWriter,
            AiUsageResultLinkService usageResultLinkService,
            AiJobHeartbeatService heartbeatService
    ) {
        this.aiJobService = aiJobService;
        this.jobRepository = jobRepository;
        this.sectionAccessGuard = sectionAccessGuard;
        this.projectAccessGuard = projectAccessGuard;
        this.contextAssembler = contextAssembler;
        this.clustererProvider = clustererProvider;
        this.resultWriter = resultWriter;
        this.usageResultLinkService = usageResultLinkService;
        this.heartbeatService = heartbeatService;
    }

    @Override
    public AiFeature feature() {
        return AiFeature.OPINION_CLUSTERING;
    }

    @Override
    public void run(UUID requestId) {
        AiJob job = loadJob(requestId);
        if (job == null || !claim(job)) {
            return;
        }
        try (AiJobHeartbeatService.HeartbeatLease ignored =
                     heartbeatService.start(requestId, feature())) {
            execute(job);
        } catch (Exception exception) {
            safeFail(requestId, exception);
        }
    }

    private void execute(AiJob job) {
        Long sectionId = job.getProjectSection().getId();
        AssembledAiContext<OpinionClusteringContext> assembled = assemble(job);
        if (!assembled.snapshot().inputSnapshotHash().equals(job.getInputSnapshotHash())) {
            aiJobService.markStale(job.getRequestId());
            return;
        }
        OpinionClusterer clusterer = clustererProvider.getIfAvailable();
        if (clusterer == null) {
            throw new AiProviderUnavailableException();
        }
        OpinionClusteringOutput output = clusterer.cluster(job, assembled.context());
        aiJobService.succeed(job.getRequestId(), () -> {
            sectionAccessGuard.requireOwnedSectionForUpdate(sectionId, job.getRequestedBy().getId());
            return assemble(job).snapshot().inputSnapshotHash();
        }, () -> {
            Long resultId = resultWriter.persist(
                    new OpinionClusteringPersistCommand(job, assembled.context(), output));
            usageResultLinkService.linkSuccessfulInvocations(job.getId(), resultId);
            return resultId;
        });
    }

    private AssembledAiContext<OpinionClusteringContext> assemble(AiJob job) {
        VerifiedProjectAccess access = projectAccessGuard.requireParticipantAccess(
                job.getProject().getId(), job.getRequestedBy().getId());
        return contextAssembler.assembleOpinionClustering(
                access, job.getProjectSection().getId());
    }

    private AiJob loadJob(UUID requestId) {
        try {
            return jobRepository.findByRequestIdWithExecutionContext(requestId).orElse(null);
        } catch (Exception exception) {
            log.warn("의견 분류 AI 작업 조회 실패 requestId={}, exceptionType={}",
                    requestId, exception.getClass().getSimpleName());
            return null;
        }
    }

    private boolean claim(AiJob job) {
        try {
            return aiJobService.start(job.getRequestId(), job.getInputSnapshotHash()).claimed();
        } catch (Exception exception) {
            log.warn("의견 분류 AI 작업 시작 실패 requestId={}, exceptionType={}",
                    job.getRequestId(), exception.getClass().getSimpleName());
            return false;
        }
    }

    private void safeFail(UUID requestId, Exception exception) {
        try {
            aiJobService.fail(requestId, exception);
        } catch (Exception failException) {
            log.warn("의견 분류 AI 작업 실패 처리 불가 requestId={}, exceptionType={}",
                    requestId, failException.getClass().getSimpleName());
        }
    }

}
