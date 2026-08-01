package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.context.ReviewIntentComparisonContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.dto.model.ReviewIntentComparisonOutput;
import com.wevo.backend.review.service.ReviewIntentComparisonStateService;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewIntentComparisonResultWriter {

    private final ReviewIntentComparisonStateService stateService;
    private final ReviewIntentComparisonOutputValidator outputValidator;

    public ReviewIntentComparisonResultWriter(
            ReviewIntentComparisonStateService stateService,
            ReviewIntentComparisonOutputValidator outputValidator
    ) {
        this.stateService = stateService;
        this.outputValidator = outputValidator;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long persist(
            AiJob job,
            ReviewIntentComparisonContext context,
            ReviewIntentComparisonOutput output
    ) {
        if (job.getFeature() != AiFeature.REVIEW_INTENT_COMPARISON
                || !job.getProjectSection().getId().equals(context.sectionId())) {
            throw new IllegalArgumentException("검토 의도 비교 저장 입력이 유효하지 않습니다.");
        }
        outputValidator.validate(
                output,
                StructuredOutputValidationContext.forSourceContents(
                        context.reviewerSummary(),
                        Set.of(context.authorIntent(), context.reviewerSummary())));
        return stateService.succeed(
                context.submissionId(),
                job,
                output.alignment(),
                output.differenceSummary(),
                output.evidenceExcerpt());
    }
}
