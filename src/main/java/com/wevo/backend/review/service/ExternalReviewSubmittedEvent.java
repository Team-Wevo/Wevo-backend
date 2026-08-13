package com.wevo.backend.review.service;

/**
 * 외부 검토 제출이 <b>정상 저장·커밋된 뒤</b> 부가 작업을 돌리기 위한 신호.
 *
 * <p>제출 트랜잭션 안에서 발행하되 소비는 커밋 이후({@code AFTER_COMMIT})다 —
 * 부가 작업의 실패가 이미 저장된 공개 제출을 되돌리지 못하게 하는 경계다.
 * ({@link ExternalReviewSubmittedEventListener} 참고)
 *
 * <p>엔티티가 아니라 <b>ID 만</b> 싣는다. 소비 시점에는 제출 트랜잭션이 이미 끝나 있으므로,
 * 소비자가 자신의 트랜잭션에서 다시 읽어야 지연 로딩이 안전하다.
 */
public record ExternalReviewSubmittedEvent(Long submissionId) {
}
