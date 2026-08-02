package com.wevo.backend.ai.client;

record OpenAiPromptCacheDecision(
        boolean enabled,
        String cacheKey,
        boolean explicit,
        String ttl
) {

    static OpenAiPromptCacheDecision disabled() {
        return new OpenAiPromptCacheDecision(false, null, false, null);
    }
}
