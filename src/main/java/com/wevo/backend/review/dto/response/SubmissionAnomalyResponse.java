package com.wevo.backend.review.dto.response;

import com.wevo.backend.review.config.ReviewAbuseProperties;
import com.wevo.backend.review.domain.ReviewSubmission;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/**
 * 외부 검토 결과의 <b>신뢰도 신호</b>. (팀장 참고용 — 확정 조건과 무관)
 *
 * <p>외부 검토는 비인증이라 제출자가 자기 신원을 스스로 정한다. 한 사람이 익명 검토자 키를
 * 바꿔가며 여러 건을 넣으면 "여러 명이 같은 평가를 했다"처럼 보이고, 링크당 20 슬롯이 그렇게 차면
 * 정상 검토자는 제출조차 하지 못한다. 집계 수치만 보면 이 오염이 드러나지 않는다.
 *
 * <p><b>IP 는 쓰지 않는다</b> — 정책서 §6.2.4가 "IP 기준 제한은 사용하지 않는다(공유망 오탐 우려)"로
 * 배제한다. 대신 <b>사람의 검토 행동과 자동화가 갈리는 두 지점</b>을 본다:
 * <ol>
 *   <li><b>제출 간격</b> — 사람은 본문을 읽고 한 문장을 쓰는 데 최소 수십 초가 걸린다. 몇 초 간격의
 *       연속 제출은 사람의 속도가 아니다.</li>
 *   <li><b>본문 중복</b> — 서로 다른 사람이 토씨까지 같은 문장을 낼 이유가 없다.</li>
 * </ol>
 *
 * <p>둘 다 <b>차단이 아니라 표시</b>다. 강의실에서 여러 명이 몰려 제출하거나 짧은 문장이 우연히
 * 겹칠 수 있으므로, 신호가 떠도 결과를 숨기거나 제출을 거부하지 않는다.
 *
 * <p>간격은 <b>같은 링크 안에서만</b> 잰다 — 재발급된 다른 링크의 제출과 섞으면 서로 무관한 두
 * 수집 구간이 인접한 것처럼 보인다.
 *
 * @param suspected                  아래 두 신호 중 하나라도 걸렸는지 (FE 배지 표시 기준)
 * @param rapidSubmissionSuspected   사람 속도가 아닌 연속 제출이 임계 이상 관측됐는지
 * @param rapidSubmissionPairs       간격이 임계 미만인 인접 제출 쌍의 수
 * @param shortestIntervalSeconds    같은 링크 내 인접 제출의 최소 간격(초) — 제출이 2건 미만이면 생략
 * @param duplicateSummarySuspected  같은 문장이 임계 이상 반복됐는지
 * @param maxIdenticalSummaryCount   가장 많이 반복된 문장의 등장 횟수
 */
@Schema(requiredProperties = {
        "suspected", "rapidSubmissionSuspected", "rapidSubmissionPairs",
        "duplicateSummarySuspected", "maxIdenticalSummaryCount"
})
public record SubmissionAnomalyResponse(
        boolean suspected,
        boolean rapidSubmissionSuspected,
        long rapidSubmissionPairs,
        Long shortestIntervalSeconds,
        boolean duplicateSummarySuspected,
        long maxIdenticalSummaryCount
) {

    /**
     * 제출 목록에서 이상 신호를 계산한다.
     *
     * @param submissions 섹션의 외부 검토 제출 (링크가 함께 로딩돼 있어야 한다)
     * @param thresholds  판정 기준 (설정 정본: {@code app.review.abuse.anomaly})
     */
    public static SubmissionAnomalyResponse from(List<ReviewSubmission> submissions,
                                                 ReviewAbuseProperties.Anomaly thresholds) {
        IntervalStats intervals = IntervalStats.of(submissions, thresholds.rapidIntervalSeconds());
        long maxIdentical = maxIdenticalSummaryCount(submissions);

        boolean rapid = intervals.rapidPairs() >= thresholds.rapidPairThreshold();
        boolean duplicate = maxIdentical >= thresholds.duplicateSummaryThreshold();

        return new SubmissionAnomalyResponse(
                rapid || duplicate,
                rapid,
                intervals.rapidPairs(),
                intervals.shortestSeconds(),
                duplicate,
                maxIdentical);
    }

    /**
     * 같은 링크 안에서 제출 시각을 정렬해 인접 간격을 센다.
     *
     * @param rapidPairs      간격이 임계 미만인 인접 쌍 수
     * @param shortestSeconds 전체에서 가장 짧은 인접 간격 — 잴 대상이 없으면 {@code null}
     */
    private record IntervalStats(long rapidPairs, Long shortestSeconds) {

        private static IntervalStats of(List<ReviewSubmission> submissions, int rapidIntervalSeconds) {
            long rapidPairs = 0;
            Long shortest = null;

            Map<Long, List<ReviewSubmission>> byLink = submissions.stream()
                    .filter(submission -> submission.getCreatedAt() != null)
                    .collect(Collectors.groupingBy(
                            submission -> submission.getReviewLink().getId()));

            for (List<ReviewSubmission> linkSubmissions : byLink.values()) {
                List<LocalDateTime> times = linkSubmissions.stream()
                        .map(ReviewSubmission::getCreatedAt)
                        .sorted(Comparator.naturalOrder())
                        .toList();

                for (int i = 1; i < times.size(); i++) {
                    long seconds = Duration.between(times.get(i - 1), times.get(i)).toSeconds();
                    if (shortest == null || seconds < shortest) {
                        shortest = seconds;
                    }
                    if (seconds < rapidIntervalSeconds) {
                        rapidPairs++;
                    }
                }
            }

            return new IntervalStats(rapidPairs, shortest);
        }
    }

    /**
     * 가장 많이 반복된 {@code summary} 의 등장 횟수.
     *
     * <p>앞뒤 공백·대소문자·연속 공백 차이는 같은 문장으로 본다 — 자동 제출이 그 정도만 바꿔도
     * 탐지를 피할 수 있게 두지 않는다. 미입력({@code UNCLEAR} 는 선택 입력)은 세지 않는다.
     */
    private static long maxIdenticalSummaryCount(List<ReviewSubmission> submissions) {
        return submissions.stream()
                .map(ReviewSubmission::getSummary)
                .filter(StringUtils::hasText)
                // 로케일 의존 소문자화(터키어 I 등)를 피해 서버 로케일과 무관하게 같은 결과를 낸다.
                .map(summary -> summary.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);
    }
}
