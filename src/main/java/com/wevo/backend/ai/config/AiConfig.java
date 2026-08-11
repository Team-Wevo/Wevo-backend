package com.wevo.backend.ai.config;

import com.wevo.backend.ai.client.OpenAiPromptCacheHttpInterceptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class AiConfig {

    @Bean
    @ConditionalOnProperty(prefix = "wevo.ai", name = "provider", havingValue = "openai")
    public ChatClient openAiChatClient(
            AiProperties properties,
            OpenAiPromptCacheHttpInterceptor promptCacheInterceptor
    ) {
        AiProperties.OpenAiOptions openai = properties.openai();
        AiProperties.ModelOptions options = properties.providerDefaultOptions();
        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .baseUrl(openai.clientBaseUrl())
                .apiKey(openai.apiKey())
                .model(options.model())
                .maxCompletionTokens(options.maxOutputTokens())
                .reasoningEffort(openai.reasoningEffort())
                .timeout(options.timeout())
                .maxRetries(0)
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .options(chatOptions)
                .httpClientBuilderCustomizer(builder -> builder.interceptor(promptCacheInterceptor))
                .build();
        return ChatClient.builder(chatModel).build();
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
     * 않게 한다. 모든 동시 실행 작업이 독립적으로 heartbeat를 보낼 수 있도록 worker 동시성만큼
     * 스레드를 확보한다.
     */
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService aiHeartbeatScheduler(AiJobDispatchProperties properties) {
        AtomicInteger threadSequence = new AtomicInteger();
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                properties.maxConcurrentJobs(),
                runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "ai-heartbeat-" + threadSequence.incrementAndGet()
                    );
                    thread.setDaemon(true);
                    return thread;
                });
        // 완료된 job의 취소 task가 다음 실행 시각까지 queue에 쌓이지 않게 한다.
        executor.setRemoveOnCancelPolicy(true);
        // shutdown 이후 heartbeat가 다시 실행돼 종료 중인 작업 상태를 건드리지 않게 한다.
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    @Bean
    public Clock aiClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
