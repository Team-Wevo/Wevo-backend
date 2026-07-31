package com.wevo.backend.ai.client;

/** Provider adapter가 공통 Chat Completions 실행 파이프라인에 전달하는 요청 옵션. */
record AiProviderRuntimeOptions(
        String providerId,
        String reasoningEffort,
        Double temperature,
        boolean nativeStrictSchema,
        boolean maxCompletionTokens
) {
}
