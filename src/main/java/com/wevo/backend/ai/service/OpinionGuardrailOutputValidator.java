package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.client.StructuredOutputValidator;
import com.wevo.backend.ai.dto.model.OpinionGuardrailVerdict;
import org.springframework.stereotype.Component;

@Component
public class OpinionGuardrailOutputValidator
        implements StructuredOutputValidator<OpinionGuardrailVerdict> {

    @Override
    public void validate(
            OpinionGuardrailVerdict output,
            StructuredOutputValidationContext context
    ) {
        if (output == null || context == null || output.reason() == null
                || !OpinionGuardrailContract.REASONS.contains(output.reason())) {
            throw new StructuredOutputSemanticException();
        }
        // acceptable 과 reason 이 어긋나면 판정을 신뢰할 수 없다 — 통과는 OK, 거부는 사유 코드여야 한다.
        boolean okReason = OpinionGuardrailContract.REASON_OK.equals(output.reason());
        if (output.acceptable() != okReason) {
            throw new StructuredOutputSemanticException();
        }
    }
}
