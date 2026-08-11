package com.wevo.backend.ai.service;

import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.AuthorIntentContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.exception.AiProviderUnavailableException;
import com.wevo.backend.ai.dto.model.AuthorIntentExtractionOutput;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class AuthorIntentJobHandler implements AiJobHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthorIntentJobHandler.class);

    private final AiJobService aiJobService;
    private final AiJobRepository aiJobRepository;
    private final SectionAccessGuard sectionAccessGuard;
    private final ProjectAccessGuard projectAccessGuard;
    private final AiContextAssembler contextAssembler;
    private final ObjectProvider<AuthorIntentExtractor> extractorProvider;
    private final AuthorIntentResultWriter resultWriter;
    private final AiUsageResultLinkService usageResultLinkService;
    private final AiJobHeartbeatService heartbeatService;

    public AuthorIntentJobHandler(
            AiJobService aiJobService,
            AiJobRepository aiJobRepository,
            SectionAccessGuard sectionAccessGuard,
            ProjectAccessGuard projectAccessGuard,
            AiContextAssembler contextAssembler,
            ObjectProvider<AuthorIntentExtractor> extractorProvider,
            AuthorIntentResultWriter resultWriter,
            AiUsageResultLinkService usageResultLinkService,
            AiJobHeartbeatService heartbeatService
    ) {
        this.aiJobService = aiJobService;
        this.aiJobRepository = aiJobRepository;
        this.sectionAccessGuard = sectionAccessGuard;
        this.projectAccessGuard = projectAccessGuard;
        this.contextAssembler = contextAssembler;
        this.extractorProvider = extractorProvider;
        this.resultWriter = resultWriter;
        this.usageResultLinkService = usageResultLinkService;
        this.heartbeatService = heartbeatService;
    }

    @Override
    public AiFeature feature() {
        return AiFeature.AUTHOR_INTENT_EXTRACTION;
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
        VerifiedProjectAccess access = access(job);
        AssembledAiContext<AuthorIntentContext> assembled =
                contextAssembler.assembleAuthorIntent(access, sectionId);
        if (!assembled.snapshot().inputSnapshotHash().equals(job.getInputSnapshotHash())) {
            aiJobService.markStale(job.getRequestId());
            return;
        }
        AuthorIntentExtractor extractor = extractorProvider.getIfAvailable();
        if (extractor == null) {
            throw new AiProviderUnavailableException();
        }
        AuthorIntentExtractionOutput output = extractor.extract(job, assembled.context());
        aiJobService.succeed(job.getRequestId(), () -> {
            sectionAccessGuard.requireOwnedSectionForUpdate(
                    sectionId, job.getRequestedBy().getId());
            return contextAssembler.assembleAuthorIntent(access(job), sectionId)
                    .snapshot().inputSnapshotHash();
        }, () -> {
            Long resultId = resultWriter.persist(job.getRequestId(), assembled.context(), output);
            usageResultLinkService.linkSuccessfulInvocations(job.getId(), resultId);
            return resultId;
        });
    }

    private VerifiedProjectAccess access(AiJob job) {
        sectionAccessGuard.requireOwnedSection(
                job.getProjectSection().getId(), job.getRequestedBy().getId());
        return projectAccessGuard.requireParticipantAccess(
                job.getProject().getId(), job.getRequestedBy().getId());
    }

    private AiJob loadJob(UUID requestId) {
        try {
            return aiJobRepository.findByRequestIdWithExecutionContext(requestId).orElse(null);
        } catch (Exception exception) {
            log.warn("작성자 의도 AI 작업 조회 실패 requestId={}, exceptionType={}",
                    requestId, exception.getClass().getSimpleName());
            return null;
        }
    }

    private boolean claim(AiJob job) {
        try {
            return aiJobService.start(job.getRequestId(), job.getInputSnapshotHash()).claimed();
        } catch (Exception exception) {
            log.warn("작성자 의도 AI 작업 시작 실패 requestId={}, exceptionType={}",
                    job.getRequestId(), exception.getClass().getSimpleName());
            return false;
        }
    }

    private void safeFail(UUID requestId, Exception exception) {
        try {
            aiJobService.fail(requestId, exception);
        } catch (Exception failException) {
            log.warn("작성자 의도 AI 작업 실패 처리 불가 requestId={}, exceptionType={}",
                    requestId, failException.getClass().getSimpleName());
        }
    }

}
