package com.wevo.backend.ai.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.review.service.ReviewIntentComparisonStateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewIntentComparisonEventListenerTest {

    @Mock private ReviewIntentComparisonRequestService requestService;
    @Mock private ReviewIntentComparisonStateService stateService;
    @InjectMocks private ReviewIntentComparisonEventListener listener;

    @Test
    void jobCreationFailureMarksOnlyComparisonFailedWithSafeCode() {
        doThrow(new IllegalStateException("provider setup failed"))
                .when(requestService).request(33L);

        listener.afterSubmissionCommitted(new ReviewIntentComparisonRequestedEvent(33L));

        verify(stateService).fail(33L, ErrorCode.AI_PROVIDER_ERROR.getCode());
    }
}
