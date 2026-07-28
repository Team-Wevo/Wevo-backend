package com.wevo.backend.ai.client;

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

    static String userPrompt(
            StructuredAiProviderRequest<?> request,
            StructuredConversionFailure previousFailure
    ) {
        StringBuilder prompt = new StringBuilder(request.prompt().userPrompt())
                .append("\n\n<output_contract>\n")
                .append("Return only one complete JSON object matching this JSON Schema:\n")
                .append(request.outputDefinition().jsonSchema())
                .append("\n</output_contract>");
        if (previousFailure != null) {
            String reason = switch (previousFailure) {
                case JSON_PARSE -> "The previous output was not valid JSON.";
                case SCHEMA_VALIDATION -> "The previous output did not match the required JSON schema.";
                case TYPE_CONVERSION -> "The previous output could not be converted to the required result type.";
            };
            prompt.append("\n\n<output_correction>\n")
                    .append(reason)
                    .append(" Return a corrected JSON object only.")
                    .append("\n</output_correction>");
        }
        return prompt.toString();
    }
}
