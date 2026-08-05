package com.wevo.backend.ai.service;

import com.wevo.backend.ai.config.AiJobDispatchProperties;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.repository.AiJobRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.wevo.backend.ai.operations.AiExecutionControl;

/**
 * QUEUED AI 작업을 <b>동시 실행 상한 안에서</b> 가상 스레드로 실행하는 실행기.
 *
 * <p>실행 슬롯을 {@link Semaphore}로 제한한다 — 폴러가 한 번에 많이 넘겨도 provider rate limit·DB
 * 커넥션·AI 비용이 폭증하지 않는다. 슬롯이 없으면 작업을 실행하지 않고 그대로 두어(QUEUED) 다음
 * 폴링에서 다시 시도한다. 작업 claim(QUEUED→RUNNING)은 핸들러가 {@link AiJobService#start}로
 * 원자적으로 수행하므로 같은 작업이 중복 제출돼도 실제 AI 호출은 한 번만 일어난다.
 */
@Component
public class AiJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(AiJobExecutor.class);

    private final ExecutorService executor;
    private final AiJobRepository aiJobRepository;
    private final Map<AiFeature, AiJobHandler> handlers;
    private final Semaphore slots;
    private AiExecutionControl executionControl;

    public AiJobExecutor(ExecutorService providerRequestExecutor,
                         AiJobRepository aiJobRepository,
                         List<AiJobHandler> handlers,
                         AiJobDispatchProperties properties) {
        this.executor = providerRequestExecutor;
        this.aiJobRepository = aiJobRepository;
        this.handlers = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(AiJobHandler::feature, Function.identity()));
        this.slots = new Semaphore(properties.maxConcurrentJobs());
    }

    /** 현재 새로 실행할 수 있는 작업 수(가용 슬롯). 폴러가 이 수만큼만 뽑도록 참고한다. */
    public int availableSlots() {
        return slots.availablePermits();
    }

    /** 실행 핸들러가 등록된 기능 집합. 폴러가 실행 가능한 작업만 뽑도록 참고한다. */
    public Set<AiFeature> supportedFeatures() {
        return handlers.keySet();
    }

    /**
     * 슬롯이 있으면 작업을 실행 스레드로 넘긴다. 슬롯이 없거나 핸들러가 없으면 실행하지 않는다(작업은
     * QUEUED로 남아 다음 폴링에서 재시도). 획득한 슬롯은 실행 완료 시 정확히 한 번 반납된다.
     */
    public void dispatch(UUID requestId) {
        if (!slots.tryAcquire()) {
            return;
        }
        try {
            AiJobHandler handler = resolveHandler(requestId);
            if (handler == null) {
                slots.release();
                return;
            }
            if (!executionAllowed(handler.feature())) {
                slots.release();
                return;
            }
            executor.submit(() -> runAndRelease(requestId, handler));
        } catch (Throwable throwable) {
            // 제출 거부(종료 중)·조회 오류 등 — 슬롯을 반납하고 다음 폴링에 맡긴다.
            slots.release();
            log.warn("AI 작업 제출 실패, 다음 폴링에서 재시도 requestId={}, exceptionType={}",
                    requestId, throwable.getClass().getSimpleName());
        }
    }

    private AiJobHandler resolveHandler(UUID requestId) {
        AiFeature feature = aiJobRepository.findByRequestId(requestId)
                .map(job -> job.getFeature())
                .orElse(null);
        if (feature == null) {
            return null;
        }
        AiJobHandler handler = handlers.get(feature);
        if (handler == null) {
            log.warn("등록된 AiJobHandler가 없어 작업을 실행할 수 없습니다. feature={}, requestId={}",
                    feature, requestId);
        }
        return handler;
    }

    private void runAndRelease(UUID requestId, AiJobHandler handler) {
        try {
            handler.run(requestId);
        } catch (Exception exception) {
            // 핸들러가 자체적으로 작업을 실패 처리한다. 여기서는 마지막 방어선으로만 기록한다.
            //
            // 예외 객체를 넘기지 않고 타입만 남긴다 — 예외 메시지에 AI 응답 원문이나 제출 의견 본문이
            // 섞여 들어올 수 있고, 프롬프트·응답 원문과 개인 식별정보는 로그에 남기지 않는다(CLAUDE.md §7).
            // 같은 이유로 이 클래스의 다른 로그도 exceptionType만 남긴다. 실패 사유는 작업 레코드의
            // finalErrorType·safeErrorMessage(세정 완료)로 확인한다.
            log.error("AI 작업 실행 중 처리되지 않은 예외 requestId={}, exceptionType={}",
                    requestId, exception.getClass().getSimpleName());
        } finally {
            slots.release();
        }
    }

    @Autowired(required = false)
    void setExecutionControl(AiExecutionControl executionControl) {
        this.executionControl = executionControl;
    }

    private boolean executionAllowed(AiFeature feature) {
        if (executionControl == null) {
            return true;
        }
        try {
            executionControl.requireDispatchAllowed(feature);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
