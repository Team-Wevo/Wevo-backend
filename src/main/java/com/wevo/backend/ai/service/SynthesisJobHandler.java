package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiJobDispatchProperties;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.dto.model.IssueDetectionIssueOutput;
import com.wevo.backend.ai.exception.AiProviderUnavailableException;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.service.SynthesisPersistCommand;
import com.wevo.backend.issue.service.SynthesisPersistCommand.InheritedGapAnswerRef;
import com.wevo.backend.issue.service.SynthesisPersistCommand.IssueSpec;
import com.wevo.backend.issue.service.SynthesisPersistCommand.RelatedOpinionSpec;
import com.wevo.backend.issue.service.SynthesisResultWriteService;
import com.wevo.backend.opinion.service.SubmittedOpinionView;
import com.wevo.backend.section.service.SectionSynthesisStateService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * AI 의견 정리 작업의 실제 실행 핸들러. (§3.8.1)
 *
 * <p>실행 순서: ① <b>먼저 작업을 claim</b>(QUEUED→RUNNING) — 이후 어떤 예외가 나도 작업이
 * {@code FAILED}로 종료되어, 조회·조립·해시 단계에서 지속적으로 실패하는 poison 작업이 QUEUED에
 * 영구 잔류해 큐를 막는 것을 방지한다. ② claim 직후부터 최종 종료(succeed·markStale·fail)까지
 * <b>RUNNING 생명주기 전체를 heartbeat로 감싼다</b> — DB 잠금·커넥션 지연으로 오래 걸려도 살아 있는
 * 작업이 회수되지 않게 한다. ③ 입력 스냅샷을 재대조해 요청 이후 입력이 바뀌었으면 STALE 종료.
 * ④ AI 호출·출력 검증. ⑤ 완료 처리 트랜잭션에서 섹션 행을 잠그고 입력을 재대조한 뒤, 통과했을 때만
 * 정리 세트 저장·재정리 플래그 해제를 원자적으로 반영.
 *
 * <p>{@code SynthesisGenerator}는 provider가 구성된 경우에만 존재하므로 {@link ObjectProvider}로
 * 지연 조회한다.
 */
@Component
public class SynthesisJobHandler implements AiJobHandler {

    private static final Logger log = LoggerFactory.getLogger(SynthesisJobHandler.class);
    private static final int MAX_EXCERPT_LENGTH = 300;

    private final AiJobService aiJobService;
    private final AiJobRepository aiJobRepository;
    private final SynthesisSnapshotAssembler snapshotAssembler;
    private final SynthesisInputHasher inputHasher;
    private final ObjectProvider<SynthesisGenerator> synthesisGeneratorProvider;
    private final SynthesisResultWriteService synthesisResultWriteService;
    private final AiUsageResultLinkService usageResultLinkService;
    private final SectionSynthesisStateService sectionSynthesisStateService;
    private final ScheduledExecutorService heartbeatScheduler;
    private final long heartbeatMillis;

    public SynthesisJobHandler(AiJobService aiJobService,
                               AiJobRepository aiJobRepository,
                               SynthesisSnapshotAssembler snapshotAssembler,
                               SynthesisInputHasher inputHasher,
                               ObjectProvider<SynthesisGenerator> synthesisGeneratorProvider,
                               SynthesisResultWriteService synthesisResultWriteService,
                               AiUsageResultLinkService usageResultLinkService,
                               SectionSynthesisStateService sectionSynthesisStateService,
                               ScheduledExecutorService aiHeartbeatScheduler,
                               AiJobDispatchProperties properties) {
        this.aiJobService = aiJobService;
        this.aiJobRepository = aiJobRepository;
        this.snapshotAssembler = snapshotAssembler;
        this.inputHasher = inputHasher;
        this.synthesisGeneratorProvider = synthesisGeneratorProvider;
        this.synthesisResultWriteService = synthesisResultWriteService;
        this.usageResultLinkService = usageResultLinkService;
        this.sectionSynthesisStateService = sectionSynthesisStateService;
        this.heartbeatScheduler = aiHeartbeatScheduler;
        // 설정 유효성(0 < interval < timeout)은 AiJobDispatchProperties가 시작 시 검증한다.
        // 여기서는 스케줄링 API가 요구하는 최소 주기(>0ms)만 보장한다.
        this.heartbeatMillis = Math.max(1, properties.heartbeatInterval().toMillis());
    }

    @Override
    public AiFeature feature() {
        return AiFeature.OPINION_SYNTHESIS;
    }

    @Override
    public void run(UUID requestId) {
        AiJob job = loadJob(requestId);
        if (job == null || !claim(requestId, job)) {
            return;
        }

        // claim 직후부터 종료까지 RUNNING 전체를 heartbeat로 감싼다. heartbeat 등록 실패(애플리케이션
        // 종료 중 executor rejection 등)도 예외 경계 안에 두어, 작업이 RUNNING으로 고립되지 않고 FAILED로
        // 종료되게 한다.
        ScheduledFuture<?> beat = null;
        try {
            beat = heartbeatScheduler.scheduleAtFixedRate(
                    () -> beat(requestId), heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
            execute(requestId, job);
        } catch (Exception exception) {
            safeFail(requestId, exception);
        } finally {
            if (beat != null) {
                beat.cancel(false);
            }
        }
    }

    private void execute(UUID requestId, AiJob job) {
        Long sectionId = job.getProjectSection().getId();
        SynthesisInputSnapshot snapshot = snapshotAssembler.assemble(sectionId);

        // 요청 이후 입력이 바뀌었으면 실행하지 않고 STALE 종료(오래된 결과 폐기).
        if (!inputHasher.hash(snapshot).equals(job.getInputSnapshotHash())) {
            aiJobService.markStale(requestId);
            return;
        }

        SynthesisAiOutput output = invokeSynthesis(job, snapshot);
        SynthesisPersistCommand command = toPersistCommand(job, sectionId, snapshot, output);

        // 완료 시점 스냅샷 재대조 — 불일치면 저장하지 않고 STALE로 종료.
        //
        // 재대조용 조회는 완료 처리 트랜잭션 <b>안에서</b> 수행한다. 밖에서 미리 계산하면 계산과 저장
        // 사이에 의견·GAP 답변이 커밋돼도 AiJob 행 잠금으로는 막히지 않아, 대조는 통과했는데 저장된
        // 결과는 낡은 입력 기준이 된다. 섹션 행을 먼저 잠가(의견 저장·제출·마감/재오픈이 잡는 것과 같은
        // 잠금) 입력 변경과 직렬화한 뒤 현재 입력을 다시 읽는다.
        aiJobService.succeed(requestId, () -> {
            sectionSynthesisStateService.lockForResultCommit(sectionId);
            return inputHasher.hash(snapshotAssembler.assemble(sectionId));
        }, () -> {
            Long setId = synthesisResultWriteService.persist(command);
            usageResultLinkService.linkSuccessfulInvocations(job.getId(), setId);
            sectionSynthesisStateService.clearSynthesisStale(sectionId);
            return setId;
        });
    }

    private AiJob loadJob(UUID requestId) {
        try {
            return aiJobRepository.findByRequestId(requestId).orElse(null);
        } catch (Exception exception) {
            // 일시 오류 — 작업은 QUEUED로 남아 다음 폴링에서 재시도된다.
            log.warn("AI 작업 조회 실패, 다음 폴링에서 재시도 requestId={}, exceptionType={}",
                    requestId, exception.getClass().getSimpleName());
            return null;
        }
    }

    /** 저장된 스냅샷 해시로 작업을 즉시 claim한다. claim 실패(이미 실행 중·종료 등)면 조용히 끝낸다. */
    private boolean claim(UUID requestId, AiJob job) {
        try {
            return aiJobService.start(requestId, job.getInputSnapshotHash()).claimed();
        } catch (Exception exception) {
            log.warn("AI 작업 시작 실패, 다음 폴링에서 재시도 requestId={}, exceptionType={}",
                    requestId, exception.getClass().getSimpleName());
            return false;
        }
    }

    private void safeFail(UUID requestId, Exception cause) {
        try {
            aiJobService.fail(requestId, cause);
        } catch (Exception failException) {
            // 이미 회수(FAILED/STALE)된 경우 등 전이 불가 — 성공 저장은 일어나지 않았으므로 상태는 안전하다.
            log.warn("AI 작업 실패 처리 불가(이미 종료 상태) requestId={}, exceptionType={}",
                    requestId, failException.getClass().getSimpleName());
        }
    }

    private void beat(UUID requestId) {
        try {
            aiJobService.heartbeat(requestId);
        } catch (Exception exception) {
            // 이미 종료 전이됐거나 일시 오류 — 다음 tick·회수 로직에 맡긴다.
            log.debug("heartbeat 갱신 실패 requestId={}", requestId);
        }
    }

    private SynthesisAiOutput invokeSynthesis(AiJob job, SynthesisInputSnapshot snapshot) {
        SynthesisGenerator generator = synthesisGeneratorProvider.getIfAvailable();
        if (generator == null) {
            throw new AiProviderUnavailableException();
        }
        return generator.generate(job, snapshot);
    }

    private SynthesisPersistCommand toPersistCommand(AiJob job, Long sectionId,
                                                     SynthesisInputSnapshot snapshot, SynthesisAiOutput output) {
        Map<Long, SubmittedOpinionView> opinionsById = snapshot.opinions().stream()
                .collect(Collectors.toMap(SubmittedOpinionView::opinionId, Function.identity()));

        List<IssueSpec> issues = output.issues().stream()
                .map(issue -> toIssueSpec(issue, opinionsById))
                .toList();
        List<RelatedOpinionSpec> consensusEvidence = output.consensusEvidenceOpinionIds().stream()
                .map(opinionId -> toRelatedOpinionSpec(opinionId, opinionsById))
                .toList();

        List<InheritedGapAnswerRef> inherited = snapshot.gapAnswers().stream()
                .map(answer -> new InheritedGapAnswerRef(answer.sourceIssueId(), answer.answerId()))
                .toList();

        return new SynthesisPersistCommand(
                job.getRequestId(),
                sectionId,
                snapshot.opinionGateGeneration(),
                output.consensusSummary(),
                consensusEvidence,
                issues,
                inherited
        );
    }

    private IssueSpec toIssueSpec(
            IssueDetectionIssueOutput issue,
            Map<Long, SubmittedOpinionView> opinionsById
    ) {
        boolean conflict = issue.type() == IssueType.CONFLICT;
        List<RelatedOpinionSpec> relatedOpinions = issue.evidenceOpinionIds().stream()
                .map(opinionId -> toRelatedOpinionSpec(opinionId, opinionsById))
                .toList();
        return new IssueSpec(
                issue.type(),
                issue.description(),
                conflict ? issue.question() : null,
                conflict ? List.copyOf(issue.options()) : List.of(),
                relatedOpinions
        );
    }

    private RelatedOpinionSpec toRelatedOpinionSpec(Long opinionId, Map<Long, SubmittedOpinionView> opinionsById) {
        SubmittedOpinionView opinion = opinionsById.get(opinionId);
        if (opinion == null) {
            // 검증기가 허용 집합 밖의 id를 걸러내므로 정상 흐름에서는 도달하지 않는다.
            throw new IllegalStateException("정리 입력에 없는 의견을 참조했습니다: " + opinionId);
        }
        return new RelatedOpinionSpec(
                opinion.opinionId(),
                opinion.authorId(),
                opinion.authorName(),
                excerpt(opinion.submittedContent())
        );
    }

    private String excerpt(String content) {
        String trimmed = content.strip();
        if (trimmed.length() <= MAX_EXCERPT_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_EXCERPT_LENGTH).strip() + "…";
    }
}
