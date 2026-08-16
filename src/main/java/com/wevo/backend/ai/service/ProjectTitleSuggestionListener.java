package com.wevo.backend.ai.service;

import com.wevo.backend.project.domain.ProjectCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 프로젝트 생성 후(커밋 완료) AI 제목 제안 job 을 요청한다.
 *
 * <p>커밋 이후에 실행해야 비동기 job 이 방금 저장된 프로젝트를 읽을 수 있다. 제목 자동 생성은
 * 부가 기능이므로 여기서 실패하더라도 생성 흐름에는 영향을 주지 않는다 — 예외를 삼키고 로그만 남긴다
 * (토큰·프롬프트 원문은 남기지 않음 — §7).
 */
@Component
public class ProjectTitleSuggestionListener {

    private static final Logger log = LoggerFactory.getLogger(ProjectTitleSuggestionListener.class);

    private final ProjectTitleRequestService requestService;

    public ProjectTitleSuggestionListener(ProjectTitleRequestService requestService) {
        this.requestService = requestService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectCreated(ProjectCreatedEvent event) {
        if (!event.titleAutoAssigned()) {
            return;
        }
        try {
            requestService.requestForProject(event.projectId(), event.ownerId());
        } catch (RuntimeException exception) {
            log.warn("프로젝트 제목 자동 생성 요청 실패 projectId={}, exceptionType={}",
                    event.projectId(), exception.getClass().getSimpleName());
        }
    }
}
