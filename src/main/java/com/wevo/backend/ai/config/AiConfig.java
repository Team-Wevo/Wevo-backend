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
import java.util.concurrent.ScheduledExecutorService;

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

    /**
     * 실행 중인 AI 작업의 heartbeat(생존 신호)를 주기적으로 갱신하는 실행기. 실행 worker가 살아
     * 있는 동안 {@code lastHeartbeatAt}을 갱신해, 재시도로 오래 걸리는 정상 작업이 회수(FAILED)되지
     * 않게 한다. heartbeat는 짧은 DB update이므로 소수 스레드로 충분하다.
     */
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService aiHeartbeatScheduler() {
        return Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "ai-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    public Clock aiClock() {
        return Clock.systemDefaultZone();
    }
}
