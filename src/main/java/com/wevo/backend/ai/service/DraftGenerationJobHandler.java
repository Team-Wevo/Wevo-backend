package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiJobDispatchProperties;
import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.DraftGenerationContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.VerifiedProjectAccess;
import com.wevo.backend.section.service.AiSectionDraftCreateCommand;
import com.wevo.backend.section.service.AiSectionDraftCreateCommand.DecisionEvidence;
import com.wevo.backend.section.service.AiSectionDraftCreateCommand.GapAnswerEvidence;
import com.wevo.backend.section.service.AiSectionDraftCreateCommand.OpinionEvidence;
import com.wevo.backend.section.service.AiSectionDraftCreateCommand.PrerequisiteEvidence;
import com.wevo.backend.section.service.AiSectionDraftWriter;
import com.wevo.backend.section.service.SectionSynthesisStateService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 초안 생성 작업을 실행하고 snapshot 재대조 후 section writer로 원자 반영한다. */
@Component
public class DraftGenerationJobHandler implements AiJobHandler {

    private static final Logger log = LoggerFactory.getLogger(DraftGenerationJobHandler.class);

    private final AiJobService aiJobService;
    private final AiJobRepository aiJobRepository;
    private final ProjectAccessGuard projectAccessGuard;
    private final AiContextAssembler contextAssembler;
    private final ObjectProvider<SectionDraftGenerator> generatorProvider;
    private final AiSectionDraftWriter draftWriter;
    private final AiUsageResultLinkService usageResultLinkService;
    private final SectionSynthesisStateService sectionStateService;
    private final ScheduledExecutorService heartbeatScheduler;
    private final long heartbeatMillis;

    public DraftGenerationJobHandler(
            AiJobService aiJobService,
            AiJobRepository aiJobRepository,
            ProjectAccessGuard projectAccessGuard,
            AiContextAssembler contextAssembler,
            ObjectProvider<SectionDraftGenerator> generatorProvider,
            AiSectionDraftWriter draftWriter,
            AiUsageResultLinkService usageResultLinkService,
            SectionSynthesisStateService sectionStateService,
            ScheduledExecutorService aiHeartbeatScheduler,
            AiJobDispatchProperties properties
    ) {
        this.aiJobService = aiJobService;
        this.aiJobRepository = aiJobRepository;
        this.projectAccessGuard = projectAccessGuard;
        this.contextAssembler = contextAssembler;
        this.generatorProvider = generatorProvider;
        this.draftWriter = draftWriter;
        this.usageResultLinkService = usageResultLinkService;
        this.sectionStateService = sectionStateService;
        this.heartbeatScheduler = aiHeartbeatScheduler;
        this.heartbeatMillis = Math.max(1, properties.heartbeatInterval().toMillis());
    }

    @Override
    public AiFeature feature() {
        return AiFeature.DRAFT_GENERATION;
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
        AssembledAiContext<DraftGenerationContext> assembled =
                contextAssembler.assembleDraftGenerationSnapshot(access, sectionId);
        if (!assembled.snapshot().inputSnapshotHash().equals(job.getInputSnapshotHash())) {
            aiJobService.markStale(job.getRequestId());
            return;
        }

        SectionDraftGenerator generator = generatorProvider.getIfAvailable();
        if (generator == null) {
            throw new IllegalStateException("AI provider가 구성되지 않아 초안을 생성할 수 없습니다.");
        }
        DraftGenerationOutput output = generator.generate(job, assembled.context());
        AiSectionDraftCreateCommand command = toCommand(job, assembled.context(), output);

        aiJobService.succeed(job.getRequestId(), () -> {
            sectionStateService.lockForResultCommit(sectionId);
            return contextAssembler.assembleDraftGenerationSnapshot(access(job), sectionId)
                    .snapshot().inputSnapshotHash();
        }, () -> {
            Long draftId = draftWriter.createAiDraft(command);
            usageResultLinkService.linkSuccessfulInvocations(job.getId(), draftId);
            return draftId;
        });
    }

    private AiSectionDraftCreateCommand toCommand(
            AiJob job,
            DraftGenerationContext context,
            DraftGenerationOutput output
    ) {
        return new AiSectionDraftCreateCommand(
                job.getProjectSection().getId(),
                job.getRequestedBy().getId(),
                output.content(),
                context.baseDraft().version(),
                context.synthesis().synthesisSetId(),
                context.synthesis().opinionGateGeneration(),
                job.getRequestId(),
                job.getInputSnapshotHash(),
                job.getSourceVersion(),
                context.synthesis().consensusSummary(),
                context.synthesis().opinionEvidence().stream()
                        .map(item -> new OpinionEvidence(
                                item.opinionId(), item.authorName(), item.content()))
                        .toList(),
                context.synthesis().conflictDecisions().stream()
                        .map(item -> new DecisionEvidence(
                                item.issueId(), item.decisionId(), item.question(), item.decision()))
                        .toList(),
                context.synthesis().gapAnswers().stream()
                        .map(item -> new GapAnswerEvidence(
                                item.sourceIssueId(),
                                item.answerId(),
                                item.authorName(),
                                item.content(),
                                LocalDateTime.parse(item.answeredAt(), DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                item.inherited()))
                        .toList(),
                context.prerequisites().stream()
                        .map(item -> new PrerequisiteEvidence(
                                item.sectionId(), item.contentVersion()))
                        .toList()
        );
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
            log.warn("AI 초안 작업 조회 실패, 다음 폴링에서 재시도 requestId={}, exceptionType={}",
                    requestId, exception.getClass().getSimpleName());
            return null;
        }
    }

    private boolean claim(UUID requestId, AiJob job) {
        try {
            return aiJobService.start(requestId, job.getInputSnapshotHash()).claimed();
        } catch (Exception exception) {
            log.warn("AI 초안 작업 시작 실패, 다음 폴링에서 재시도 requestId={}, exceptionType={}",
                    requestId, exception.getClass().getSimpleName());
            return false;
        }
    }

    private void safeFail(UUID requestId, Exception cause) {
        try {
            aiJobService.fail(requestId, cause);
        } catch (Exception failException) {
            log.warn("AI 초안 작업 실패 처리 불가 requestId={}, exceptionType={}",
                    requestId, failException.getClass().getSimpleName());
        }
    }

    private void heartbeat(UUID requestId) {
        try {
            aiJobService.heartbeat(requestId);
        } catch (Exception exception) {
            log.debug("AI 초안 작업 heartbeat 갱신 실패 requestId={}", requestId);
        }
    }
}
