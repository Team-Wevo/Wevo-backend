package com.wevo.backend.review.service;

import com.wevo.backend.review.repository.ReviewSubmissionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 커밋된 제출 한 건에 대해 <b>독립 트랜잭션</b>에서 의도 비교 상태를 준비한다.
 *
 * <p><b>{@code REQUIRES_NEW} 여야 한다</b> — 이 메서드는 제출 트랜잭션의 커밋 이후
 * ({@link ExternalReviewSubmittedEventListener}) 호출되는데, 그 시점에도 끝난 트랜잭션의 자원은
 * 아직 스레드에 묶여 있다. {@code REQUIRED} 로 두면 <b>이미 커밋된 트랜잭션에 참여</b>해
 * 여기서 저장한 비교 상태가 커밋되지 않고 조용히 사라진다.
 *
 * <p>이 경계가 곧 격리 지점이다 — 비교 준비가 실패하면 <b>이 트랜잭션만</b> 롤백되고,
 * 이미 커밋된 제출은 영향을 받지 않는다. (트랜잭션 롤백 표시는 호출자가 예외를 잡아도 되돌릴 수
 * 없으므로, 예외를 삼키는 지점은 이 트랜잭션 <b>바깥</b>이어야 한다 — 그래서 예외 처리는 리스너에
 * 두고 이 클래스는 그대로 던진다.)
 *
 * <p>제출은 ID 로 다시 읽는다. 발행 시점의 엔티티는 제출 트랜잭션과 함께 영속성 컨텍스트가 닫혀
 * 지연 로딩이 성립하지 않기 때문이다. 이미 커밋된 제출이라 정상 경로에서는 항상 존재하지만,
 * 없으면(수동 정리 등) 조용히 건너뛴다.
 */
@Component
public class ReviewIntentComparisonPreparer {

    private final ReviewSubmissionRepository reviewSubmissionRepository;
    private final ReviewIntentComparisonCoordinator comparisonCoordinator;

    public ReviewIntentComparisonPreparer(
            ReviewSubmissionRepository reviewSubmissionRepository,
            ReviewIntentComparisonCoordinator comparisonCoordinator
    ) {
        this.reviewSubmissionRepository = reviewSubmissionRepository;
        this.comparisonCoordinator = comparisonCoordinator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void prepare(Long submissionId) {
        reviewSubmissionRepository.findById(submissionId)
                .ifPresent(comparisonCoordinator::prepare);
    }
}
