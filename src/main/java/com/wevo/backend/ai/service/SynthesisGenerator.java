package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.context.AiGapAnswerContext;
import com.wevo.backend.ai.context.AiOpinionContext;
import com.wevo.backend.ai.context.ContextChunkPlan;
import com.wevo.backend.ai.context.ContextChunkPlanner;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.prompt.SynthesisPromptFactory;
import com.wevo.backend.ai.service.SynthesisPromptContext.PartialSynthesis;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.issue.service.GapAnswerInputView;
import com.wevo.backend.opinion.service.SubmittedOpinionView;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/**
 * opinion synthesis의 부분 호출과 최종 병합을 수행한다.
 *
 * <p>부분 결과는 영속화하지 않는다. 모든 chunk가 성공하고 최종 결과가 전체 eligible opinion
 * coverage를 선언해 의미 검증을 통과한 경우에만 결과를 반환한다.</p>
 */
@Service
@ConditionalOnBean(AiInvocationService.class)
public class SynthesisGenerator {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ContextChunkPlanner chunkPlanner;
    private final SynthesisPromptFactory promptFactory;
    private final AiInvocationService invocationService;

    public SynthesisGenerator(
            ContextChunkPlanner chunkPlanner,
            SynthesisPromptFactory promptFactory,
            AiInvocationService invocationService
    ) {
        this.chunkPlanner = chunkPlanner;
        this.promptFactory = promptFactory;
        this.invocationService = invocationService;
    }

    public SynthesisAiOutput generate(AiJob job, SynthesisInputSnapshot snapshot) {
        if (job == null || snapshot == null) {
            throw new IllegalArgumentException("AI 작업과 synthesis snapshot은 필수입니다.");
        }
        if (snapshot.hasNoOpinion()) {
            throw new BusinessException(ErrorCode.NO_SUBMITTED_OPINION);
        }

        List<AiOpinionContext> opinions = toAiOpinions(snapshot.opinions());
        List<AiGapAnswerContext> gapAnswers = toGapAnswers(snapshot.gapAnswers());
        ContextChunkPlan plan = chunkPlanner.plan(
                AiFeature.OPINION_SYNTHESIS,
                opinions,
                chunkOpinions -> {
                    SynthesisPromptContext context = partialContext(chunkOpinions, gapAnswers);
                    return promptFactory.tokenBudgetInput(context, opinionIds(chunkOpinions));
                }
        );

        AiUsageStartCommand usage = usageStartCommand(job);
        List<PartialSynthesis> partials = new ArrayList<>();
        for (var chunk : plan.chunks()) {
            SynthesisPromptContext context = partialContext(chunk.opinions(), gapAnswers);
            SynthesisAiOutput output = invoke(
                    usage,
                    promptFactory.providerRequest(context, Set.copyOf(chunk.opinionIds()))
            );
            partials.add(new PartialSynthesis(chunk.index(), chunk.opinionIds(), output));
        }

        if (partials.size() == 1) {
            return partials.getFirst().output();
        }

        SynthesisPromptContext mergeContext = new SynthesisPromptContext(
                SynthesisPromptContext.FINAL_MERGE,
                List.of(),
                gapAnswers,
                partials
        );
        return invoke(
                usage,
                promptFactory.providerRequest(
                        mergeContext,
                        Set.copyOf(plan.eligibleOpinionIds())
                )
        );
    }

    private SynthesisAiOutput invoke(
            AiUsageStartCommand usage,
            StructuredAiProviderRequest<SynthesisAiOutput> request
    ) {
        return invocationService.invokeStructured(
                usage,
                request,
                response -> new AiProcessedResult<>(response.result(), null)
        ).value();
    }

    private AiUsageStartCommand usageStartCommand(AiJob job) {
        return new AiUsageStartCommand(
                job,
                job.getProject(),
                job.getProjectSection(),
                job.getRequestedBy(),
                AiFeature.OPINION_SYNTHESIS,
                job.getPromptVersion(),
                job.getInputSnapshotHash()
        );
    }

    private SynthesisPromptContext partialContext(
            List<AiOpinionContext> opinions,
            List<AiGapAnswerContext> gapAnswers
    ) {
        return new SynthesisPromptContext(
                SynthesisPromptContext.PARTIAL,
                opinions,
                gapAnswers,
                List.of()
        );
    }

    private List<AiOpinionContext> toAiOpinions(List<SubmittedOpinionView> submitted) {
        List<SubmittedOpinionView> sorted = new ArrayList<>(submitted);
        for (SubmittedOpinionView opinion : sorted) {
            if (opinion == null
                    || opinion.opinionId() == null
                    || opinion.authorId() == null
                    || opinion.submittedAt() == null
                    || opinion.submittedContent() == null
                    || opinion.submittedContent().isBlank()) {
                throw new IllegalStateException("synthesis 제출 의견 입력이 유효하지 않습니다.");
            }
        }
        sorted.sort(Comparator.comparing(SubmittedOpinionView::submittedAt)
                .thenComparing(SubmittedOpinionView::opinionId));
        Map<Long, String> aliases = new LinkedHashMap<>();
        return sorted.stream()
                .map(opinion -> {
                    String alias = aliases.computeIfAbsent(
                            opinion.authorId(),
                            ignored -> "member-" + (aliases.size() + 1)
                    );
                    return new AiOpinionContext(
                            opinion.opinionId(),
                            alias,
                            opinion.submittedContent(),
                            DATE_TIME_FORMAT.format(opinion.submittedAt())
                    );
                })
                .toList();
    }

    private List<AiGapAnswerContext> toGapAnswers(List<GapAnswerInputView> answers) {
        return answers.stream()
                .map(answer -> {
                    if (answer == null
                            || answer.sourceIssueId() == null
                            || answer.answerId() == null
                            || answer.content() == null
                            || answer.content().isBlank()) {
                        throw new IllegalStateException("synthesis GAP 답변 입력이 유효하지 않습니다.");
                    }
                    return new AiGapAnswerContext(
                            answer.sourceIssueId(),
                            answer.answerId(),
                            answer.content(),
                            null
                    );
                })
                .toList();
    }

    private Set<Long> opinionIds(List<AiOpinionContext> opinions) {
        return opinions.stream().map(AiOpinionContext::opinionId).collect(Collectors.toSet());
    }
}
