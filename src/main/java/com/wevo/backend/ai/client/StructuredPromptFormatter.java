package com.wevo.backend.ai.client;

import com.wevo.backend.ai.context.AiTokenBudgetInput;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 구조화 출력 Provider에 전달되는 user prompt의 단일 렌더링 규칙.
 *
 * <p>chunk planner와 실제 gateway가 같은 문자열을 계산하도록 공용 경계로 둔다.</p>
 */
public final class StructuredPromptFormatter {

    private StructuredPromptFormatter() {
    }

    public static String initialUserPrompt(StructuredAiProviderRequest<?> request) {
        return userPrompt(request, null);
    }

    /** 최초 호출 전에 교정 재시도의 최대 prompt 증가분까지 함께 검사할 입력을 만든다. */
    public static AiTokenBudgetInput tokenBudgetInput(
            StructuredAiProviderRequest<?> request,
            boolean correctionPossible
    ) {
        if (request == null) {
            throw new IllegalArgumentException("구조화 출력 요청은 필수입니다.");
        }
        if (!correctionPossible) {
            return AiTokenBudgetInput.of(
                    request.prompt().systemPrompt(),
                    initialUserPrompt(request));
        }
        return AiTokenBudgetInput.of(
                request.prompt().systemPrompt(),
                initialUserPrompt(request),
                correctionReserve());
    }

    static String userPrompt(
            StructuredAiProviderRequest<?> request,
            StructuredConversionFailure previousFailure
    ) {
        return userPrompt(request, previousFailure, null);
    }

    static String userPrompt(
            StructuredAiProviderRequest<?> request,
            StructuredConversionFailure previousConversionFailure,
            StructuredOutputSemanticFailureReason previousSemanticFailure
    ) {
        StringBuilder prompt = new StringBuilder(request.prompt().userPrompt())
                .append("\n\n<output_contract>\n")
                .append("Return only one complete JSON object matching this JSON Schema:\n")
                .append(request.outputDefinition().jsonSchema())
                .append("\n</output_contract>");
        if (previousConversionFailure != null) {
            prompt.append(correctionBlock(previousConversionFailure));
        } else if (previousSemanticFailure != null) {
            prompt.append(correctionBlock(previousSemanticFailure));
        }
        return prompt.toString();
    }

    private static String correctionReserve() {
        String conversionReserve = Arrays.stream(StructuredConversionFailure.values())
                .map(StructuredPromptFormatter::correctionBlock)
                .max(java.util.Comparator.comparingInt(
                        value -> value.getBytes(StandardCharsets.UTF_8).length))
                .orElseThrow();
        String semanticReserve = Arrays.stream(StructuredOutputSemanticFailureReason.values())
                .map(StructuredPromptFormatter::correctionBlock)
                .max(java.util.Comparator.comparingInt(
                        value -> value.getBytes(StandardCharsets.UTF_8).length))
                .orElseThrow();
        return conversionReserve.getBytes(StandardCharsets.UTF_8).length
                >= semanticReserve.getBytes(StandardCharsets.UTF_8).length
                ? conversionReserve
                : semanticReserve;
    }

    private static String correctionBlock(StructuredConversionFailure failure) {
        String reason = switch (failure) {
            case JSON_PARSE -> "The previous output was not valid JSON.";
            case SCHEMA_VALIDATION -> "The previous output did not match the required JSON schema.";
            case TYPE_CONVERSION ->
                    "The previous output could not be converted to the required result type.";
        };
        return "\n\n<output_correction>\n"
                + reason
                + " Return a corrected JSON object only."
                + "\n</output_correction>";
    }

    private static String correctionBlock(StructuredOutputSemanticFailureReason failure) {
        return "\n\n<output_correction>\n"
                + "The previous output failed semantic validation ("
                + failure.name()
                + "). Re-check references, uniqueness, collection limits, and coverage against "
                + "the provided input. Return a corrected JSON object only."
                + "\n</output_correction>";
    }
}
