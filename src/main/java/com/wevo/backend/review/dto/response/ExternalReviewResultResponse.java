package com.wevo.backend.review.dto.response;

import com.wevo.backend.review.domain.ReviewSubmission;
import com.wevo.backend.review.domain.ReviewIntentComparison;
import com.wevo.backend.review.domain.UnderstandingSignal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.function.Function;

/**
 * 섹션의 외부 검토 결과 집계 + 개별 목록. (팀장 참고용)
 *
 * <p>확정 조건에는 포함되지 않으며, 팀장이 이해도 분포와 코멘트를 참고하기 위한 조회 결과다.
 *
 * <p>링크는 발급 시점 본문 버전에 고정되므로, 재발급 후에는 한 섹션에 서로 다른 버전의 제출이
 * 공존할 수 있다. 전체 합산({@code totalCount} 등)만으로는 "이미 고쳐진 본문에 대한 평가"가
 * 최신 본문 평가와 섞이므로, 버전별 집계({@code byVersion} — 최신 버전 먼저)를 함께 내려
 * 어느 본문에 대한 결과인지 구분할 수 있게 한다.
 *
 * @param totalCount    전체 제출 수 (모든 버전 합산)
 * @param clearCount    이해됨(CLEAR) 수 (모든 버전 합산)
 * @param partialCount  애매함(PARTIAL) 수 (모든 버전 합산)
 * @param unclearCount  이해 어려움(UNCLEAR) 수 (모든 버전 합산)
 * @param byVersion     검토한 본문 버전별 집계 (최신 버전 먼저)
 * @param items         제출 목록(최신순)
 */
public record ExternalReviewResultResponse(
        long totalCount,
        long clearCount,
        long partialCount,
        long unclearCount,
        List<VersionCountResponse> byVersion,
        List<ExternalReviewItemResponse> items
) {

    /**
     * 본문 버전 하나에 대한 이해도 집계.
     *
     * @param contentVersion 검토한 본문 버전 (링크에 고정된 발급 시점 버전)
     * @param totalCount     이 버전에 대한 제출 수
     * @param clearCount     이해됨(CLEAR) 수
     * @param partialCount   애매함(PARTIAL) 수
     * @param unclearCount   이해 어려움(UNCLEAR) 수
     */
    public record VersionCountResponse(
            Integer contentVersion,
            long totalCount,
            long clearCount,
            long partialCount,
            long unclearCount
    ) {

        private static VersionCountResponse of(Integer contentVersion, List<ReviewSubmission> submissions) {
            return new VersionCountResponse(
                    contentVersion,
                    submissions.size(),
                    count(submissions, UnderstandingSignal.CLEAR),
                    count(submissions, UnderstandingSignal.PARTIAL),
                    count(submissions, UnderstandingSignal.UNCLEAR));
        }
    }

    public static ExternalReviewResultResponse from(
            List<ReviewSubmission> submissions,
            List<ReviewIntentComparison> comparisons
    ) {
        Map<Long, ReviewIntentComparison> comparisonBySubmission = comparisons.stream()
                .collect(Collectors.toMap(
                        comparison -> comparison.getReviewSubmission().getId(),
                        Function.identity()));
        List<ExternalReviewItemResponse> items = submissions.stream()
                .map(submission -> ExternalReviewItemResponse.from(
                        submission, comparisonBySubmission.get(submission.getId())))
                .toList();

        // 검토한 버전별로 묶어 최신 버전부터 집계한다. (TreeMap 내림차순)
        Map<Integer, List<ReviewSubmission>> groupedByVersion = submissions.stream()
                .collect(Collectors.groupingBy(
                        submission -> submission.getReviewLink().getContentVersion(),
                        () -> new TreeMap<>(Comparator.<Integer>reverseOrder()),
                        Collectors.toList()));
        List<VersionCountResponse> byVersion = groupedByVersion.entrySet().stream()
                .map(entry -> VersionCountResponse.of(entry.getKey(), entry.getValue()))
                .toList();

        return new ExternalReviewResultResponse(
                submissions.size(),
                count(submissions, UnderstandingSignal.CLEAR),
                count(submissions, UnderstandingSignal.PARTIAL),
                count(submissions, UnderstandingSignal.UNCLEAR),
                byVersion,
                items);
    }

    //제출된 이해도 집계
    private static long count(List<ReviewSubmission> submissions, UnderstandingSignal signal) {
        return submissions.stream()
                .filter(s -> s.getUnderstandingSignal() == signal)
                .count();
    }
}
