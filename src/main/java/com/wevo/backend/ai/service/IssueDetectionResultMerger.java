package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.context.ContextChunkPlan;
import com.wevo.backend.ai.dto.model.IssueDetectionOutput;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * AI 최종 병합 결과를 전체 opinion 집합으로 재검증하고 coverage와 결합한다.
 */
@Component
public class IssueDetectionResultMerger {

    private final IssueDetectionOutputValidator validator;

    public IssueDetectionResultMerger(IssueDetectionOutputValidator validator) {
        this.validator = validator;
    }

    public IssueDetectionResult merge(
            ContextChunkPlan plan,
            IssueDetectionOutput finalOutput
    ) {
        if (plan == null || finalOutput == null) {
            throw new IllegalArgumentException("chunk 계획과 최종 출력은 필수입니다.");
        }
        validator.validate(
                finalOutput,
                new StructuredOutputValidationContext(Set.copyOf(plan.eligibleOpinionIds()))
        );
        return new IssueDetectionResult(
                finalOutput.issues(),
                plan.eligibleOpinionIds(),
                plan.coveredOpinionIds()
        );
    }
}
