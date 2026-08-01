package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.client.StructuredOutputValidator;
import com.wevo.backend.ai.dto.model.AuthorIntentExtractionOutput;
import com.wevo.backend.section.domain.AuthorIntentTextPolicy;
import org.springframework.stereotype.Component;

@Component
public class AuthorIntentExtractionOutputValidator
        implements StructuredOutputValidator<AuthorIntentExtractionOutput> {

    @Override
    public void validate(
            AuthorIntentExtractionOutput output,
            StructuredOutputValidationContext context
    ) {
        if (output == null || context == null || !AuthorIntentTextPolicy.isValid(output.intent())) {
            throw new StructuredOutputSemanticException();
        }
    }
}
