package com.wevo.backend.ai.service;

import com.wevo.backend.ai.context.AiContextAssembler;
import com.wevo.backend.ai.context.AssembledAiContext;
import com.wevo.backend.ai.context.DraftGenerationContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.exception.AiProviderUnavailableException;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.issue.service.CurrentSynthesisContext;
import com.wevo.backend.issue.service.SynthesisSetQueryService;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.SectionAccessGuard;
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
    private final SectionAccessGuard sectionAccessGuard;
    private final SynthesisSetQueryService synthesisSetQueryService;
    private final AiContextAssembler contextAssembler;
    private final ObjectProvider<SectionDraftGenerator> generatorProvider;
    private final AiSectionDraftWriter draftWriter;
    private final AiUsageResultLinkService usageResultLinkService;
    private final SectionSynthesisStateService sectionStateService;
    private final AiJobHeartbeatService heartbeatService;

    public DraftGenerationJobHandler(
            AiJobService aiJobService,
            AiJobRepository aiJobRepository,
            ProjectAccessGuard projectAccessGuard,
            SectionAccessGuard sectionAccessGuard,
            SynthesisSetQueryService synthesisSetQueryService,
            AiContextAssembler contextAssembler,
            ObjectProvider<SectionDraftGenerator> generatorProvider,
            AiSectionDraftWriter draftWriter,
            AiUsageResultLinkService usageResultLinkService,
            SectionSynthesisStateService sectionStateService,
            AiJobHeartbeatService heartbeatService
    ) {
        this.aiJobService = aiJobService;
        this.aiJobRepository = aiJobRepository;
        this.projectAccessGuard = projectAccessGuard;
        this.sectionAccessGuard = sectionAccessGuard;
        this.synthesisSetQueryService = synthesisSetQueryService;
        this.contextAssembler = contextAssembler;
        this.generatorProvider = generatorProvider;
        this.draftWriter = draftWriter;
        this.usageResultLinkService = usageResultLinkService;
        this.sectionStateService = sectionStateService;
        this.heartbeatService = heartbeatService;
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
        AssembledAiContext<DraftGenerationContext> assembled =
                contextAssembler.assembleDraftGenerationSnapshot(access, sectionId);
        if (!assembled.snapshot().inputSnapshotHash().equals(job.getInputSnapshotHash())) {
            aiJobService.markStale(job.getRequestId());
            return;
        }

        SectionDraftGenerator generator = generatorProvider.getIfAvailable();
        if (generator == null) {
            throw new AiProviderUnavailableException();
        }
        DraftGenerationOutput output = generator.generate(job, assembled.context());
        CurrentSynthesisContext evidenceSnapshot =
                synthesisSetQueryService.getCurrentForDraftGeneration(
                        sectionAccessGuard.verifySectionAccess(access, sectionId));
        AiSectionDraftCreateCommand command =
                toCommand(job, assembled.context(), output, evidenceSnapshot);

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
            DraftGenerationOutput output,
            CurrentSynthesisContext evidenceSnapshot
    ) {
        requireSameSynthesis(context, evidenceSnapshot);
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
                                item.opinionId(),
                                requireOpinionAuthorName(evidenceSnapshot, item.opinionId()),
                                item.content()))
                        .toList(),
                context.synthesis().conflictDecisions().stream()
                        .map(item -> new DecisionEvidence(
                                item.issueId(), item.decisionId(), item.question(), item.decision()))
                        .toList(),
                context.synthesis().gapAnswers().stream()
                        .map(item -> new GapAnswerEvidence(
                                item.sourceIssueId(),
                                item.answerId(),
                                requireAnswerAuthorName(evidenceSnapshot, item.answerId()),
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

    private void requireSameSynthesis(
            DraftGenerationContext context,
            CurrentSynthesisContext evidenceSnapshot
    ) {
        if (evidenceSnapshot == null
                || !context.synthesis().synthesisSetId().equals(evidenceSnapshot.synthesisSetId())
                || context.synthesis().opinionGateGeneration()
                != evidenceSnapshot.opinionGateGeneration()) {
            throw new IllegalStateException("초안 생성 근거 snapshot이 AI 입력과 일치하지 않습니다.");
        }
    }

    private String requireOpinionAuthorName(
            CurrentSynthesisContext evidenceSnapshot,
            Long opinionId
    ) {
        return evidenceSnapshot.opinionEvidence().stream()
                .filter(item -> opinionId.equals(item.opinionId()))
                .map(item -> item.authorNameSnapshot())
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "초안 생성 의견 근거의 작성자 snapshot이 누락되었습니다."));
    }

    private String requireAnswerAuthorName(
            CurrentSynthesisContext evidenceSnapshot,
            Long answerId
    ) {
        return evidenceSnapshot.gapAnswers().stream()
                .filter(item -> answerId.equals(item.answerId()))
                .map(item -> item.authorNameSnapshot())
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "초안 생성 GAP 답변의 작성자 snapshot이 누락되었습니다."));
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

}
