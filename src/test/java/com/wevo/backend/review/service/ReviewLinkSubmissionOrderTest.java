package com.wevo.backend.review.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.security.TokenHasher;
import com.wevo.backend.project.service.ProjectAccessGuard;
import com.wevo.backend.project.service.SectionAccessGuard;
import com.wevo.backend.review.domain.UnderstandingSignal;
import com.wevo.backend.review.dto.request.ExternalReviewSubmitRequest;
import com.wevo.backend.review.repository.ReviewLinkRepository;
import com.wevo.backend.review.repository.ReviewSubmissionRepository;
import com.wevo.backend.section.repository.SectionDraftRepository;
import com.wevo.backend.section.service.SectionAuthorIntentQueryService;
import com.wevo.backend.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 제출 경로에서 <b>속도 제한이 링크 식별 뒤에 온다</b>는 순서를 고정한다.
 *
 * <p>식별 전에 세면 아무 문자열이나 토큰으로 보내는 요청마다 카운터 키가 생겨(분·시간 2개씩)
 * Redis 메모리가 공격자 마음대로 늘어난다. 순서가 되돌아가면 이 테스트가 깨진다.
 */
@ExtendWith(MockitoExtension.class)
class ReviewLinkSubmissionOrderTest {

    private static final String UNKNOWN_TOKEN = "존재하지-않는-토큰";
    private static final String TOKEN_HASH = "e3b0c44298fc1c149afbf4c8996fb924";
    private static final String REVIEWER_KEY = "6f2b0f1e-6b0a-4c1e-9f3a-2f4d5b6c7a80";

    @Mock private SectionAccessGuard sectionAccessGuard;
    @Mock private ProjectAccessGuard projectAccessGuard;
    @Mock private SectionDraftRepository sectionDraftRepository;
    @Mock private ReviewLinkRepository reviewLinkRepository;
    @Mock private ReviewSubmissionRepository reviewSubmissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private TokenHasher tokenHasher;
    @Mock private SectionAuthorIntentQueryService authorIntentQueryService;
    @Mock private ReviewIntentComparisonCoordinator comparisonCoordinator;
    @Mock private PublicSubmissionRateLimiter rateLimiter;

    @InjectMocks
    private ReviewLinkService reviewLinkService;

    @Test
    @DisplayName("존재하지 않는 토큰의 제출은 카운터를 만들지 않고 R001 로 끝난다")
    void unknownToken_doesNotTouchRateLimiter() {
        given(tokenHasher.hash(UNKNOWN_TOKEN)).willReturn(TOKEN_HASH);
        given(reviewLinkRepository.findByTokenHashForUpdate(TOKEN_HASH)).willReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> reviewLinkService.submitExternalReview(UNKNOWN_TOKEN, REVIEWER_KEY, request()));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_LINK_NOT_FOUND);
        // 토큰을 무한히 바꿔 보내도 카운터 저장소에는 아무것도 남지 않아야 한다.
        verifyNoInteractions(rateLimiter);
    }

    private ExternalReviewSubmitRequest request() {
        return new ExternalReviewSubmitRequest(UnderstandingSignal.CLEAR, null, "이해했습니다.", null);
    }
}
