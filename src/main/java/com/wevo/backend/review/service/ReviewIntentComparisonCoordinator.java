package com.wevo.backend.review.service;

import com.wevo.backend.review.domain.ReviewSubmission;

/** 공개 제출 트랜잭션 안에서 비교 상태만 준비하고 Provider 실행은 커밋 뒤로 미루는 경계. */
public interface ReviewIntentComparisonCoordinator {

    void prepare(ReviewSubmission submission);
}
