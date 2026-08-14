package com.wevo.backend.ai.service;

import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.ProjectTitleContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.dto.model.ProjectTitleSuggestionOutput;
import com.wevo.backend.ai.exception.AiProviderUnavailableException;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class ProjectTitleJobHandler implements AiJobHandler {

    private static final Logger log = LoggerFactory.getLogger(ProjectTitleJobHandler.class);

    private final AiJobService aiJobService;
    private final AiJobRepository aiJobRepository;
    private final ProjectAccessGuard projectAccessGuard;
    private final AiContextAssembler contextAssembler;
    private final ObjectProvider<ProjectTitleExtractor> extractorProvider;
    private final ProjectTitleResultWriter resultWriter;
    private final AiUsageResultLinkService usageResultLinkService;
    private final AiJobHeartbeatService heartbeatService;

    public ProjectTitleJobHandler(
            AiJobService aiJobService,
            AiJobRepository aiJobRepository,
            ProjectAccessGuard projectAccessGuard,
            AiContextAssembler contextAssembler,
            ObjectProvider<ProjectTitleExtractor> extractorProvider,
            ProjectTitleResultWriter resultWriter,
            AiUsageResultLinkService usageResultLinkService,
            AiJobHeartbeatService heartbeatService
    ) {
        this.aiJobService = aiJobService;
        this.aiJobRepository = aiJobRepository;
        this.projectAccessGuard = projectAccessGuard;
        this.contextAssembler = contextAssembler;
        this.extractorProvider = extractorProvider;
        this.resultWriter = resultWriter;
        this.usageResultLinkService = usageResultLinkService;
        this.heartbeatService = heartbeatService;
    }

    @Override
    public AiFeature feature() {
        return AiFeature.PROJECT_TITLE_SUGGESTION;
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
        Long projectId = job.getProject().getId();
        Long requestedById = job.getRequestedBy().getId();
        AssembledAiContext<ProjectTitleContext> assembled = contextAssembler.assembleProjectTitle(
                projectAccessGuard.requireParticipantAccess(projectId, requestedById));
        ProjectTitleExtractor extractor = extractorProvider.getIfAvailable();
        if (extractor == null) {
            throw new AiProviderUnavailableException();
        }
        ProjectTitleSuggestionOutput output = extractor.generate(job, assembled.context());
        aiJobService.succeed(job.getRequestId(),
                () -> contextAssembler.assembleProjectTitle(
                        projectAccessGuard.requireParticipantAccess(projectId, requestedById))
                        .snapshot().inputSnapshotHash(),
                () -> {
                    Long resultId = resultWriter.persist(
                            job.getRequestId(), assembled.context(), output);
                    usageResultLinkService.linkSuccessfulInvocations(job.getId(), resultId);
                    return resultId;
                });
    }

    private AiJob loadJob(UUID requestId) {
        try {
            return aiJobRepository.findByRequestIdWithExecutionContext(requestId).orElse(null);
        } catch (Exception exception) {
            log.warn("프로젝트 제목 AI 작업 조회 실패 requestId={}, exceptionType={}",
                    requestId, exception.getClass().getSimpleName());
            return null;
        }
    }

    private boolean claim(AiJob job) {
        try {
            return aiJobService.start(job.getRequestId(), job.getInputSnapshotHash()).claimed();
        } catch (Exception exception) {
            log.warn("프로젝트 제목 AI 작업 시작 실패 requestId={}, exceptionType={}",
                    job.getRequestId(), exception.getClass().getSimpleName());
            return false;
        }
    }

    private void safeFail(UUID requestId, Exception exception) {
        try {
            aiJobService.fail(requestId, exception);
        } catch (Exception failException) {
            log.warn("프로젝트 제목 AI 작업 실패 처리 불가 requestId={}, exceptionType={}",
                    requestId, failException.getClass().getSimpleName());
        }
    }
}
