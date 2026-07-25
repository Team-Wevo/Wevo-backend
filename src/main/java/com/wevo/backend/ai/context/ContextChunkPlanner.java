package com.wevo.backend.ai.context;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.domain.AiFeature;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 실제 렌더러를 callback으로 받아 의견 단위의 결정적 greedy chunk를 만든다.
 *
 * <p>동일한 정렬 입력과 설정에서는 같은 경계를 만들며, 단일 의견 초과 시 절단하지 않고 실패한다.</p>
 */
@Component
public class ContextChunkPlanner {

    private final AiProperties properties;
    private final AiTokenBudgetEstimator estimator;

    public ContextChunkPlanner(AiProperties properties, AiTokenBudgetEstimator estimator) {
        this.properties = properties;
        this.estimator = estimator;
    }

    public ContextChunkPlan plan(
            AiFeature feature,
            List<AiOpinionContext> eligibleOpinions,
            Function<List<AiOpinionContext>, AiTokenBudgetInput> renderedInputFactory
    ) {
        if (feature == null || eligibleOpinions == null || renderedInputFactory == null) {
            throw new IllegalArgumentException("AI feature, eligible opinions, input renderer는 필수입니다.");
        }
        if (!AiProperties.ModelOptions.REJECT_OVERSIZED_INPUT_V1.equals(
                properties.optionsFor(feature).singleInputOverflowPolicy())) {
            throw new IllegalStateException("지원하지 않는 단일 AI 입력 초과 정책입니다.");
        }

        List<AiOpinionContext> sorted = new ArrayList<>(eligibleOpinions);
        validateUniqueOpinionIds(sorted);
        sorted.sort(Comparator.comparing(AiOpinionContext::submittedAt)
                .thenComparing(AiOpinionContext::opinionId));

        if (sorted.isEmpty()) {
            AiTokenBudgetEstimate estimate = estimator.requireWithinBudget(
                    feature,
                    requireRenderedInput(renderedInputFactory, List.of())
            );
            ContextChunk emptyChunk = new ContextChunk(1, List.of(), List.of(), estimate.estimatedInputTokens());
            return new ContextChunkPlan(List.of(emptyChunk), List.of(), List.of(), true);
        }

        List<ContextChunk> chunks = new ArrayList<>();
        List<AiOpinionContext> current = new ArrayList<>();
        for (AiOpinionContext opinion : sorted) {
            List<AiOpinionContext> candidate = new ArrayList<>(current);
            candidate.add(opinion);
            AiTokenBudgetEstimate estimate = estimator.estimate(
                    feature,
                    requireRenderedInput(renderedInputFactory, List.copyOf(candidate))
            );
            if (estimate.withinBudget()) {
                current = candidate;
                continue;
            }
            if (current.isEmpty()) {
                throw oversized(feature, estimate);
            }
            chunks.add(chunk(chunks.size() + 1, current, feature, renderedInputFactory));

            List<AiOpinionContext> singleton = List.of(opinion);
            AiTokenBudgetEstimate singletonEstimate = estimator.estimate(
                    feature,
                    requireRenderedInput(renderedInputFactory, singleton)
            );
            if (!singletonEstimate.withinBudget()) {
                throw oversized(feature, singletonEstimate);
            }
            current = new ArrayList<>(singleton);
        }
        chunks.add(chunk(chunks.size() + 1, current, feature, renderedInputFactory));

        List<Long> eligibleIds = sorted.stream().map(AiOpinionContext::opinionId).toList();
        List<Long> coveredIds = chunks.stream()
                .flatMap(chunk -> chunk.opinionIds().stream())
                .toList();
        return new ContextChunkPlan(chunks, eligibleIds, coveredIds, true);
    }

    private ContextChunk chunk(
            int index,
            List<AiOpinionContext> opinions,
            AiFeature feature,
            Function<List<AiOpinionContext>, AiTokenBudgetInput> renderedInputFactory
    ) {
        List<AiOpinionContext> immutable = List.copyOf(opinions);
        AiTokenBudgetEstimate estimate = estimator.requireWithinBudget(
                feature,
                requireRenderedInput(renderedInputFactory, immutable)
        );
        return new ContextChunk(
                index,
                immutable,
                immutable.stream().map(AiOpinionContext::opinionId).toList(),
                estimate.estimatedInputTokens()
        );
    }

    private void validateUniqueOpinionIds(List<AiOpinionContext> opinions) {
        Set<Long> ids = new HashSet<>();
        for (AiOpinionContext opinion : opinions) {
            if (opinion == null
                    || opinion.opinionId() == null
                    || !StringUtils.hasText(opinion.submittedAt())
                    || !StringUtils.hasText(opinion.authorAlias())
                    || !StringUtils.hasText(opinion.submittedContent())
                    || !ids.add(opinion.opinionId())) {
                throw new IllegalArgumentException("eligible opinion 식별값은 null 또는 중복일 수 없습니다.");
            }
        }
    }

    private AiTokenBudgetInput requireRenderedInput(
            Function<List<AiOpinionContext>, AiTokenBudgetInput> renderedInputFactory,
            List<AiOpinionContext> opinions
    ) {
        AiTokenBudgetInput input = renderedInputFactory.apply(opinions);
        if (input == null) {
            throw new IllegalStateException("AI context 렌더러가 token 계산 입력을 반환하지 않았습니다.");
        }
        return input;
    }

    private AiInputBudgetExceededException oversized(
            AiFeature feature,
            AiTokenBudgetEstimate estimate
    ) {
        return new AiInputBudgetExceededException(
                feature,
                estimate.estimatedInputTokens(),
                estimate.maxInputTokens()
        );
    }
}
