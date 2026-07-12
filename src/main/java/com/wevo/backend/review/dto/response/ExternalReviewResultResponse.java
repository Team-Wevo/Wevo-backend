package com.wevo.backend.review.dto.response;

import com.wevo.backend.review.domain.ReviewSubmission;
import com.wevo.backend.review.domain.UnderstandingSignal;
import java.util.List;

/**
 * 섹션의 외부 검토 결과 집계 + 개별 목록. (팀장 참고용)
 *
 * <p>확정 조건에는 포함되지 않으며, 팀장이 이해도 분포와 코멘트를 참고하기 위한 조회 결과다.
 *
 * @param totalCount    전체 제출 수
 * @param clearCount    이해됨(CLEAR) 수
 * @param partialCount  애매함(PARTIAL) 수
 * @param unclearCount  이해 어려움(UNCLEAR) 수
 * @param items         제출 목록(최신순)
 */
public record ExternalReviewResultResponse(
        long totalCount,
        long clearCount,
        long partialCount,
        long unclearCount,
        List<ExternalReviewItemResponse> items
) {

    public static ExternalReviewResultResponse from(List<ReviewSubmission> submissions) {
        List<ExternalReviewItemResponse> items = submissions.stream()
                .map(ExternalReviewItemResponse::from)
                .toList();
        return new ExternalReviewResultResponse(
                submissions.size(),
                count(submissions, UnderstandingSignal.CLEAR),
                count(submissions, UnderstandingSignal.PARTIAL),
                count(submissions, UnderstandingSignal.UNCLEAR),
                items);
    }

    //제출된 이해도 집계
    private static long count(List<ReviewSubmission> submissions, UnderstandingSignal signal) {
        return submissions.stream()
                .filter(s -> s.getUnderstandingSignal() == signal)
                .count();
    }
}
