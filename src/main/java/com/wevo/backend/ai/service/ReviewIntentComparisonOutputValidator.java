package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.client.StructuredOutputValidator;
import com.wevo.backend.ai.dto.model.ReviewIntentComparisonOutput;
import org.springframework.stereotype.Component;

@Component
public class ReviewIntentComparisonOutputValidator
        implements StructuredOutputValidator<ReviewIntentComparisonOutput> {

    public static final int MAX_DIFFERENCE_LENGTH = 1000;
    public static final int MAX_EVIDENCE_LENGTH = 300;

    @Override
    public void validate(
            ReviewIntentComparisonOutput output,
            StructuredOutputValidationContext context
    ) {
        if (output == null
                || context == null
                || output.alignment() == null
                || !validText(output.differenceSummary(), MAX_DIFFERENCE_LENGTH)) {
            throw reject();
        }
        String evidence = output.evidenceExcerpt();
        if (evidence != null && (!validText(evidence, MAX_EVIDENCE_LENGTH)
                || context.allowedSourceContents().stream().noneMatch(source -> source.contains(evidence)))) {
            throw reject();
        }
    }

    private boolean validText(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    private StructuredOutputSemanticException reject() {
        return new StructuredOutputSemanticException();
    }
}
