package com.wevo.backend.review.service;

import com.wevo.backend.review.domain.ReviewSubmission;

/**
 * 비교 상태만 준비하고 Provider 실행은 그 커밋 뒤로 미루는 경계.
 *
 * <p>호출 계약 — 제출 트랜잭션이 <b>아니라</b> 제출 커밋 이후의 독립 트랜잭션에서 호출된다.
 * ({@link ReviewIntentComparisonPreparer}) 여기서 나는 실패는 그 트랜잭션만 되돌리며,
 * 이미 저장된 공개 제출에는 영향을 주지 않는다.
 */
public interface ReviewIntentComparisonCoordinator {

    void prepare(ReviewSubmission submission);
}
