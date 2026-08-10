package com.wevo.backend.review.dto.response;

import com.wevo.backend.review.config.ReviewAbuseProperties;
import com.wevo.backend.review.domain.ReviewLink;
import com.wevo.backend.review.domain.ReviewSubmission;
import com.wevo.backend.review.domain.UnderstandingSignal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IP 없이 위조 제출을 알아보는 신호를 검증한다. (정책서 §6.2.4 — IP 기준 제한 미사용)
 *
 * <p>사람과 자동화가 갈리는 두 지점을 본다: <b>제출 간격</b>과 <b>본문 중복</b>.
 */
class SubmissionAnomalyResponseTest {

    private static final ReviewAbuseProperties.Anomaly THRESHOLDS =
            new ReviewAbuseProperties.Anomaly(10, 3, 2);
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 8, 10, 14, 0);

    @Test
    @DisplayName("몇 초 간격의 연속 제출이 임계 이상이면 자동 제출을 의심한다")
    void flagsRapidSubmissions() {
        ReviewLink link = link(1L);
        // 2초 간격 4건 → 임계(10초) 미만 인접 쌍 3개
        List<ReviewSubmission> submissions = List.of(
                submission(link, BASE, "문장 A"),
                submission(link, BASE.plusSeconds(2), "문장 B"),
                submission(link, BASE.plusSeconds(4), "문장 C"),
                submission(link, BASE.plusSeconds(6), "문장 D"));

        SubmissionAnomalyResponse anomaly = SubmissionAnomalyResponse.from(submissions, THRESHOLDS);

        assertThat(anomaly.rapidSubmissionSuspected()).isTrue();
        assertThat(anomaly.rapidSubmissionPairs()).isEqualTo(3);
        assertThat(anomaly.shortestIntervalSeconds()).isEqualTo(2);
        assertThat(anomaly.suspected()).isTrue();
    }

    @Test
    @DisplayName("사람이 읽고 쓰는 간격이면 의심하지 않는다")
    void doesNotFlagHumanPacedSubmissions() {
        ReviewLink link = link(1L);
        List<ReviewSubmission> submissions = List.of(
                submission(link, BASE, "문장 A"),
                submission(link, BASE.plusMinutes(3), "문장 B"),
                submission(link, BASE.plusMinutes(7), "문장 C"));

        SubmissionAnomalyResponse anomaly = SubmissionAnomalyResponse.from(submissions, THRESHOLDS);

        assertThat(anomaly.rapidSubmissionSuspected()).isFalse();
        assertThat(anomaly.rapidSubmissionPairs()).isZero();
        assertThat(anomaly.suspected()).isFalse();
    }

    @Test
    @DisplayName("동시에 낸 한두 건은 의심하지 않는다 — 강의실 오탐 방지")
    void allowsFewSimultaneousSubmissions() {
        ReviewLink link = link(1L);
        List<ReviewSubmission> submissions = List.of(
                submission(link, BASE, "문장 A"),
                submission(link, BASE.plusSeconds(3), "문장 B"),
                submission(link, BASE.plusMinutes(5), "문장 C"));

        SubmissionAnomalyResponse anomaly = SubmissionAnomalyResponse.from(submissions, THRESHOLDS);

        assertThat(anomaly.rapidSubmissionPairs()).isEqualTo(1);
        assertThat(anomaly.rapidSubmissionSuspected()).isFalse();
    }

    @Test
    @DisplayName("간격은 같은 링크 안에서만 잰다 — 재발급된 링크의 제출과 섞지 않는다")
    void measuresIntervalsWithinEachLink() {
        // 서로 다른 수집 구간의 제출이 시각상 인접하다는 이유로 "연속 제출"이 되면 안 된다.
        List<ReviewSubmission> submissions = List.of(
                submission(link(1L), BASE, "문장 A"),
                submission(link(2L), BASE.plusSeconds(1), "문장 B"),
                submission(link(3L), BASE.plusSeconds(2), "문장 C"));

        SubmissionAnomalyResponse anomaly = SubmissionAnomalyResponse.from(submissions, THRESHOLDS);

        assertThat(anomaly.rapidSubmissionPairs()).isZero();
        assertThat(anomaly.shortestIntervalSeconds()).isNull();
    }

    @Test
    @DisplayName("같은 문장이 반복되면 의심한다 — 공백·대소문자 차이는 같은 문장으로 본다")
    void flagsDuplicateSummaries() {
        ReviewLink link = link(1L);
        List<ReviewSubmission> submissions = List.of(
                submission(link, BASE, "장학금 매칭 서비스로 이해했습니다."),
                submission(link, BASE.plusMinutes(5), "  장학금  매칭 서비스로 이해했습니다.  "),
                submission(link, BASE.plusMinutes(9), "다른 문장입니다."));

        SubmissionAnomalyResponse anomaly = SubmissionAnomalyResponse.from(submissions, THRESHOLDS);

        assertThat(anomaly.duplicateSummarySuspected()).isTrue();
        assertThat(anomaly.maxIdenticalSummaryCount()).isEqualTo(2);
        assertThat(anomaly.suspected()).isTrue();
        // 간격은 사람 속도라 이쪽 신호는 꺼져 있어야 한다.
        assertThat(anomaly.rapidSubmissionSuspected()).isFalse();
    }

    @Test
    @DisplayName("문장이 서로 다르면 중복으로 보지 않는다")
    void doesNotFlagDistinctSummaries() {
        ReviewLink link = link(1L);
        List<ReviewSubmission> submissions = List.of(
                submission(link, BASE, "문장 A"),
                submission(link, BASE.plusMinutes(5), "문장 B"));

        SubmissionAnomalyResponse anomaly = SubmissionAnomalyResponse.from(submissions, THRESHOLDS);

        assertThat(anomaly.duplicateSummarySuspected()).isFalse();
        assertThat(anomaly.maxIdenticalSummaryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("미입력 문장(UNCLEAR 선택 입력)은 중복으로 세지 않는다")
    void ignoresMissingSummaries() {
        ReviewLink link = link(1L);
        List<ReviewSubmission> submissions = List.of(
                submission(link, BASE, null),
                submission(link, BASE.plusMinutes(5), null),
                submission(link, BASE.plusMinutes(9), "   "));

        SubmissionAnomalyResponse anomaly = SubmissionAnomalyResponse.from(submissions, THRESHOLDS);

        assertThat(anomaly.duplicateSummarySuspected()).isFalse();
        assertThat(anomaly.maxIdenticalSummaryCount()).isZero();
    }

    @Test
    @DisplayName("제출이 없거나 1건이면 신호가 서지 않는다")
    void handlesEmptyAndSingleSubmission() {
        SubmissionAnomalyResponse empty = SubmissionAnomalyResponse.from(List.of(), THRESHOLDS);
        assertThat(empty.suspected()).isFalse();
        assertThat(empty.shortestIntervalSeconds()).isNull();
        assertThat(empty.maxIdenticalSummaryCount()).isZero();

        SubmissionAnomalyResponse single = SubmissionAnomalyResponse.from(
                List.of(submission(link(1L), BASE, "문장 A")), THRESHOLDS);
        assertThat(single.suspected()).isFalse();
        assertThat(single.shortestIntervalSeconds()).isNull();
    }

    private ReviewLink link(Long id) {
        ReviewLink link = ReviewLink.builder().build();
        ReflectionTestUtils.setField(link, "id", id);

        return link;
    }

    private ReviewSubmission submission(ReviewLink link, LocalDateTime createdAt, String summary) {
        ReviewSubmission submission = ReviewSubmission.builder()
                .reviewLink(link)
                .understandingSignal(UnderstandingSignal.CLEAR)
                .summary(summary)
                .build();
        ReflectionTestUtils.setField(submission, "createdAt", createdAt);

        return submission;
    }
}
