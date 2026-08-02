package com.wevo.backend.ai.client;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.AiTokenBudgetEstimator;
import com.wevo.backend.ai.exception.OpenAiExceptionTranslator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

@Component
@ConditionalOnProperty(prefix = "wevo.ai", name = "provider", havingValue = "openai")
public class SpringAiOpenAiGateway extends SpringAiNvidiaGateway {

    public static final String PROVIDER_ID = "openai";

    public SpringAiOpenAiGateway(
            @Qualifier("openAiChatClient") ChatClient chatClient,
            AiProperties properties,
            OpenAiExceptionTranslator exceptionTranslator,
            ExecutorService providerRequestExecutor,
            AiUsageExtractor usageExtractor,
            AiRetrySleeper retrySleeper,
            AiTokenBudgetEstimator tokenBudgetEstimator,
            OpenAiPromptCachePolicy promptCachePolicy
    ) {
        super(
                chatClient,
                properties,
                new AiProviderRuntimeOptions(
                        PROVIDER_ID,
                        properties.openai().reasoningEffort(),
                        null,
                        true,
                        true,
                        promptCachePolicy
                ),
                exceptionTranslator,
                providerRequestExecutor,
                usageExtractor,
                retrySleeper,
                tokenBudgetEstimator
        );
    }
}
