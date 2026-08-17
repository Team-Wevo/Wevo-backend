package com.wevo.backend.review.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 커밋 이후 콜백에서 던진 예외는 커밋 호출부까지 거슬러 올라가 이미 성공한 제출을 500 으로
 * 만든다. 이 리스너가 마지막 방어선이라는 계약을 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class ExternalReviewSubmittedEventListenerTest {

    @Mock private ReviewIntentComparisonPreparer comparisonPreparer;
    @InjectMocks private ExternalReviewSubmittedEventListener listener;

    @Test
    @DisplayName("비교 준비가 실패해도 예외를 밖으로 내보내지 않는다")
    void preparationFailureIsContained() {
        doThrow(new DataIntegrityViolationException("비교 저장 실패"))
                .when(comparisonPreparer).prepare(33L);

        assertThatCode(() -> listener.afterSubmissionCommitted(
                new ExternalReviewSubmittedEvent(33L)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("제출 ID 로 비교 준비를 위임한다")
    void delegatesPreparationBySubmissionId() {
        listener.afterSubmissionCommitted(new ExternalReviewSubmittedEvent(33L));

        verify(comparisonPreparer).prepare(33L);
    }
}
