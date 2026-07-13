package com.wevo.backend.ai.config;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
public class AiConfig {

    @Bean
    public ChatClient claudeChatClient(ChatClient.Builder builder, AiProperties properties) {
        AiProperties.ModelOptions options = properties.defaultOptions();
        return builder
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(options.model())
                        .maxTokens(options.maxOutputTokens()))
                .build();
    }

    /**
     * 기능별 deadline을 적용하기 위한 실행기.
     * Claude 호출의 비동기 작업 수명주기는 AI-04에서 별도로 오케스트레이션한다.
     */
    @Bean(destroyMethod = "close")
    public ExecutorService claudeRequestExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
