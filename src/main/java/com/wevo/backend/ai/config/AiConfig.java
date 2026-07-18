package com.wevo.backend.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class AiConfig {

    @Bean
    @ConditionalOnProperty(prefix = "wevo.ai", name = "provider", havingValue = "nvidia")
    public ChatClient nvidiaChatClient(
            ChatClient.Builder builder,
            AiProperties properties,
            NvidiaProviderProperties nvidiaProperties
    ) {
        AiProperties.ModelOptions options = properties.defaultOptions();
        return builder
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(options.model())
                        .maxTokens(options.maxOutputTokens())
                        .n(1)
                        .temperature(nvidiaProperties.temperature())
                        .reasoningEffort(nvidiaProperties.reasoningEffort())
                        .timeout(options.timeout())
                        .customHeaders(Map.of("Accept", "application/json"))
                        .maxRetries(0))
                .build();
    }

    /**
     * 기능별 deadline을 적용하기 위한 실행기.
     * Provider 호출의 비동기 작업 수명주기는 AI-04에서 별도로 오케스트레이션한다.
     */
    @Bean(destroyMethod = "close")
    public ExecutorService providerRequestExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public Clock aiClock() {
        return Clock.systemDefaultZone();
    }
}
