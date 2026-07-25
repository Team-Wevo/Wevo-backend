package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiFeature;
import java.util.UUID;

/**
 * 큐잉된 AI 작업을 실제로 실행하는 기능별 핸들러. (synthesis·draft-generation·precheck가 각자 구현)
 *
 * <p>{@link AiJobExecutor}가 커밋 후 가상 스레드에서 {@link #run(UUID)}을 호출한다. 구현체는
 * 작업 시작({@code start})→AI 호출→성공({@code succeed}) 수명주기와 스냅샷 재대조를
 * {@link AiJobService}를 통해 처리한다.
 */
public interface AiJobHandler {

    /** 이 핸들러가 담당하는 AI 기능. 기능당 핸들러는 하나여야 한다. */
    AiFeature feature();

    /** 큐잉된 작업 하나를 실행한다. 예외는 실행기가 잡아 작업 실패로 종료시킨다. */
    void run(UUID requestId);
}
