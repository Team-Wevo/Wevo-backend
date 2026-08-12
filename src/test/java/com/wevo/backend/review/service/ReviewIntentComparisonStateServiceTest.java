package com.wevo.backend.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.review.domain.ReviewIntentAlignment;
import com.wevo.backend.review.domain.ReviewIntentComparison;
import com.wevo.backend.review.repository.ReviewIntentComparisonRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewIntentComparisonStateServiceTest {

    @Mock private ReviewIntentComparisonRepository repository;
    @Mock private ReviewIntentComparison comparison;
    @InjectMocks private ReviewIntentComparisonStateService stateService;

    @Test
    void succeedRejectsResultFromDifferentSourceJob() {
        given(repository.findByReviewSubmissionIdForUpdate(33L))
                .willReturn(Optional.of(comparison));
        given(comparison.getSourceAiJobId()).willReturn(44L);

        BusinessException exception = catchThrowableOfType(
                () -> stateService.succeed(
                        33L,
                        45L,
                        ReviewIntentAlignment.ALIGNED,
                        "차이 없음",
                        "근거"),
                BusinessException.class);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
        verify(comparison, never()).succeed(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
