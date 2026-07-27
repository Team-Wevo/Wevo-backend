package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.config.AiJobDispatchProperties;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.repository.AiJobRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiJobExecutorTest {

    private ExecutorService executor;
    private AiJobRepository aiJobRepository;

    @BeforeEach
    void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        aiJobRepository = mock(AiJobRepository.class);
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    private AiJobDispatchProperties props(int maxConcurrentJobs) {
        return new AiJobDispatchProperties(true, Duration.ofSeconds(2), 20, maxConcurrentJobs,
                Duration.ofSeconds(15), Duration.ofSeconds(60), Duration.ofSeconds(60));
    }

    private AiJobHandler handlerFor(AiFeature feature) {
        AiJobHandler handler = mock(AiJobHandler.class);
        given(handler.feature()).willReturn(feature);
        return handler;
    }

    private void givenJobFeature(UUID requestId, AiFeature feature) {
        AiJob job = mock(AiJob.class);
        given(job.getFeature()).willReturn(feature);
        given(aiJobRepository.findByRequestId(requestId)).willReturn(Optional.of(job));
    }

    @Test
    @DisplayName("작업의 기능에 맞는 핸들러로 실행한다")
    void dispatch_runsMatchingHandler() throws InterruptedException {
        UUID requestId = UUID.randomUUID();
        givenJobFeature(requestId, AiFeature.OPINION_SYNTHESIS);
        AiJobHandler handler = handlerFor(AiFeature.OPINION_SYNTHESIS);
        CountDownLatch ran = new CountDownLatch(1);
        willAnswer(invocation -> {
            ran.countDown();
            return null;
        }).given(handler).run(requestId);

        AiJobExecutor jobExecutor = new AiJobExecutor(executor, aiJobRepository, List.of(handler), props(4));
        jobExecutor.dispatch(requestId);

        assertThat(ran.await(3, TimeUnit.SECONDS)).isTrue();
        verify(handler).run(requestId);
    }

    @Test
    @DisplayName("등록된 핸들러가 없으면 실행하지 않고 슬롯을 반납한다")
    void dispatch_noHandler_releasesSlot() {
        UUID requestId = UUID.randomUUID();
        givenJobFeature(requestId, AiFeature.OPINION_SYNTHESIS);

        AiJobExecutor jobExecutor = new AiJobExecutor(executor, aiJobRepository, List.of(), props(4));
        jobExecutor.dispatch(requestId);

        assertThat(jobExecutor.availableSlots()).isEqualTo(4); // 슬롯 반납됨
    }

    @Test
    @DisplayName("동시 실행 상한을 초과하는 작업은 실행하지 않는다(슬롯이 빌 때까지 QUEUED 유지)")
    void dispatch_respectsConcurrencyLimit() throws InterruptedException {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        givenJobFeature(first, AiFeature.OPINION_SYNTHESIS);
        givenJobFeature(second, AiFeature.OPINION_SYNTHESIS);
        AiJobHandler handler = handlerFor(AiFeature.OPINION_SYNTHESIS);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch gate = new CountDownLatch(1);
        willAnswer(invocation -> {
            started.countDown();
            gate.await(3, TimeUnit.SECONDS);
            return null;
        }).given(handler).run(first);

        AiJobExecutor jobExecutor = new AiJobExecutor(executor, aiJobRepository, List.of(handler), props(1));

        jobExecutor.dispatch(first);   // 슬롯 1개 점유(동기) 후 실행 시작
        assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(jobExecutor.availableSlots()).isZero();

        jobExecutor.dispatch(second);  // 슬롯 없음 → 실행되지 않아야 함

        verify(handler).run(first);
        verify(handler, never()).run(second);

        gate.countDown();
        // first 완료 후 슬롯이 반납된다.
        assertThat(awaitSlot(jobExecutor)).isTrue();
    }

    private boolean awaitSlot(AiJobExecutor jobExecutor) throws InterruptedException {
        for (int i = 0; i < 30; i++) {
            if (jobExecutor.availableSlots() >= 1) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }
}
