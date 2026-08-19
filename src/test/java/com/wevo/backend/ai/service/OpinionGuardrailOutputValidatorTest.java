package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.dto.model.OpinionGuardrailVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpinionGuardrailOutputValidatorTest {

    private final OpinionGuardrailOutputValidator validator = new OpinionGuardrailOutputValidator();
    private final StructuredOutputValidationContext context = StructuredOutputValidationContext.empty();

    @Test
    @DisplayName("통과 판정(acceptable=true, reason=OK)은 유효하다")
    void acceptableOk_passes() {
        assertThatCode(() -> validator.validate(new OpinionGuardrailVerdict(true, "OK"), context))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("거부 판정(acceptable=false, reason=GIBBERISH/OFF_TOPIC)은 유효하다")
    void rejectWithReason_passes() {
        assertThatCode(() -> validator.validate(new OpinionGuardrailVerdict(false, "GIBBERISH"), context))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new OpinionGuardrailVerdict(false, "OFF_TOPIC"), context))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("acceptable 과 reason 이 어긋나면 거부한다 (통과인데 사유가 있거나, 거부인데 OK)")
    void mismatchedAcceptableAndReason_throws() {
        assertThatThrownBy(() -> validator.validate(new OpinionGuardrailVerdict(true, "GIBBERISH"), context))
                .isInstanceOf(StructuredOutputSemanticException.class);
        assertThatThrownBy(() -> validator.validate(new OpinionGuardrailVerdict(false, "OK"), context))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }

    @Test
    @DisplayName("허용되지 않은 사유 값이나 null 은 거부한다")
    void unknownOrNullReason_throws() {
        assertThatThrownBy(() -> validator.validate(new OpinionGuardrailVerdict(false, "SPAM"), context))
                .isInstanceOf(StructuredOutputSemanticException.class);
        assertThatThrownBy(() -> validator.validate(new OpinionGuardrailVerdict(false, null), context))
                .isInstanceOf(StructuredOutputSemanticException.class);
        assertThatThrownBy(() -> validator.validate(null, context))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }
}
