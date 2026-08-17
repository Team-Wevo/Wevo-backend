package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.client.StructuredOutputValidator;
import com.wevo.backend.ai.dto.model.ProjectTitleSuggestionOutput;
import org.springframework.stereotype.Component;

@Component
public class ProjectTitleOutputValidator
        implements StructuredOutputValidator<ProjectTitleSuggestionOutput> {

    @Override
    public void validate(
            ProjectTitleSuggestionOutput output,
            StructuredOutputValidationContext context
    ) {
        if (output == null || context == null || !ProjectTitleContract.isValidTitle(output.title())) {
            throw new StructuredOutputSemanticException();
        }
    }
}
