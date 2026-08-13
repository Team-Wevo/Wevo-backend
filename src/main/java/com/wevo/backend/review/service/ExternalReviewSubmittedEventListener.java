package com.wevo.backend.review.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 제출이 <b>DB 에 커밋된 뒤</b> 의도 비교 준비를 실행한다.
 *
 * <p>공개 제출은 검토자에게 다시 요청할 수 없는 1회성 입력이라, 부가 기능(의도 비교)의 실패로
 * 잃어서는 안 된다. 준비 작업을 제출 트랜잭션 <b>안</b>에서 돌리면 그 안에서 난 예외를 여기서
 * 잡아도 소용이 없다 — 같은 트랜잭션에 참여한 하위 작업의 실패는 트랜잭션을 rollback-only 로
 * 표시하고, 커밋 시점에 {@code UnexpectedRollbackException} 으로 제출까지 함께 롤백된다.
 * 그래서 준비 작업 전체를 커밋 이후로 미룬다.
 *
 * <p><b>예외를 잡는 위치</b>가 계약의 핵심이다 — 실제 저장은
 * {@link ReviewIntentComparisonPreparer} 의 독립 트랜잭션이 맡고, 여기서는 그 트랜잭션
 * <b>바깥</b>에서 예외를 삼킨다. 안에서 삼키면 rollback-only 표시가 남아 같은 문제가 재발한다.
 *
 * <p>커밋 이후 콜백에서 던진 예외는 커밋 호출부까지 거슬러 올라가 이미 성공한 요청을 500 으로
 * 만들므로, 여기서 반드시 끊는다. 실패는 비교가 생기지 않는 <b>조용한 실패</b>로 남고
 * (팀장 화면에는 {@code NOT_AVAILABLE} 로 보인다), 제출 자체는 그대로 보존된다.
 * 검토자 원문·summary 가 예외 메시지에 섞일 수 있으므로 예외 타입만 기록한다. (CLAUDE.md §7)
 */
@Component
public class ExternalReviewSubmittedEventListener {

    private static final Logger log =
            LoggerFactory.getLogger(ExternalReviewSubmittedEventListener.class);

    private final ReviewIntentComparisonPreparer comparisonPreparer;

    public ExternalReviewSubmittedEventListener(
            ReviewIntentComparisonPreparer comparisonPreparer) {
        this.comparisonPreparer = comparisonPreparer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterSubmissionCommitted(ExternalReviewSubmittedEvent event) {
        try {
            comparisonPreparer.prepare(event.submissionId());
        } catch (RuntimeException exception) {
            log.warn("검토 의도 비교 준비 실패 submissionId={}, exceptionType={}",
                    event.submissionId(), exception.getClass().getSimpleName());
        }
    }
}
