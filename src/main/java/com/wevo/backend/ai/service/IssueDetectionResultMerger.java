package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.context.ContextChunk;
import com.wevo.backend.ai.context.ContextChunkPlan;
import com.wevo.backend.ai.dto.model.IssueDetectionIssueOutput;
import com.wevo.backend.ai.dto.model.IssueDetectionOutput;
import com.wevo.backend.issue.domain.IssueType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * chunk별 쟁점 후보를 입력 순서에 따라 결정적으로 병합한다.
 *
 * <p>자유 텍스트 유사도는 사용하지 않고 타입·문구·질문·선택지가 정확히 같은 후보만 합친다.</p>
 */
@Component
public class IssueDetectionResultMerger {

    private final IssueDetectionOutputValidator validator;

    public IssueDetectionResultMerger(IssueDetectionOutputValidator validator) {
        this.validator = validator;
    }

    public IssueDetectionResult merge(
            ContextChunkPlan plan,
            List<IssueDetectionOutput> chunkOutputs
    ) {
        if (plan == null
                || chunkOutputs == null
                || chunkOutputs.size() != plan.chunks().size()) {
            throw new IllegalArgumentException("chunk 계획과 출력 개수가 일치해야 합니다.");
        }

        Map<IssueKey, MergedIssue> merged = new LinkedHashMap<>();
        for (int index = 0; index < plan.chunks().size(); index++) {
            ContextChunk chunk = plan.chunks().get(index);
            IssueDetectionOutput output = chunkOutputs.get(index);
            validator.validate(
                    output,
                    new StructuredOutputValidationContext(Set.copyOf(chunk.opinionIds()))
            );
            for (IssueDetectionIssueOutput issue : output.issues()) {
                IssueKey key = IssueKey.from(issue);
                merged.computeIfAbsent(key, ignored -> new MergedIssue(issue))
                        .addEvidence(issue.evidenceOpinionIds());
            }
        }

        List<IssueDetectionIssueOutput> issues = merged.values().stream()
                .map(value -> value.toOutput(plan.eligibleOpinionIds()))
                .toList();
        IssueDetectionOutput output = new IssueDetectionOutput(issues);
        validator.validate(
                output,
                new StructuredOutputValidationContext(Set.copyOf(plan.eligibleOpinionIds()))
        );
        return new IssueDetectionResult(
                output.issues(),
                plan.eligibleOpinionIds(),
                plan.coveredOpinionIds()
        );
    }

    private record IssueKey(
            IssueType type,
            String description,
            String question,
            List<String> options
    ) {

        private static IssueKey from(IssueDetectionIssueOutput issue) {
            return new IssueKey(
                    issue.type(),
                    issue.description(),
                    issue.question(),
                    issue.options()
            );
        }
    }

    private static final class MergedIssue {

        private final IssueDetectionIssueOutput first;
        private final Set<Long> evidenceIds = new LinkedHashSet<>();

        private MergedIssue(IssueDetectionIssueOutput first) {
            this.first = first;
        }

        private void addEvidence(List<Long> ids) {
            evidenceIds.addAll(ids);
        }

        private IssueDetectionIssueOutput toOutput(List<Long> eligibleOpinionIds) {
            List<Long> orderedEvidence = new ArrayList<>();
            for (Long opinionId : eligibleOpinionIds) {
                if (evidenceIds.contains(opinionId)) {
                    orderedEvidence.add(opinionId);
                }
            }
            if (orderedEvidence.size() != evidenceIds.size()) {
                throw new StructuredOutputSemanticException();
            }
            return new IssueDetectionIssueOutput(
                    first.type(),
                    first.description(),
                    orderedEvidence,
                    first.question(),
                    first.options()
            );
        }
    }
}
