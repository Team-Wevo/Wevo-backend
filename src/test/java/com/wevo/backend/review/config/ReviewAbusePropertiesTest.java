package com.wevo.backend.review.config;

import com.wevo.backend.review.config.ReviewAbuseProperties.Anomaly;
import com.wevo.backend.review.config.ReviewAbuseProperties.RateLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 설정값 검증. 잘못된 값이 기동을 통과하면 <b>보호 장치가 있는 것처럼 보이지만 실제로는 없는</b>
 * 상태가 되므로, 조용히 넘어가지 않는지 확인한다.
 */
class ReviewAbusePropertiesTest {

    private static final RateLimit VALID_LIMIT = new RateLimit(10, 40, true);
    private static final Anomaly VALID_ANOMALY = new Anomaly(10, 3, 2);

    @Test
    @DisplayName("속도 제한 설정이 없으면 기동에 실패한다")
    void rejectsMissingRateLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReviewAbuseProperties(null, VALID_ANOMALY));
    }

    @Test
    @DisplayName("이상 신호 설정이 없으면 기동에 실패한다")
    void rejectsMissingAnomaly() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReviewAbuseProperties(VALID_LIMIT, null));
    }

    @Test
    @DisplayName("한도가 0 이하면 기동에 실패한다 — 정상 제출까지 전부 막히기 때문")
    void rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReviewAbuseProperties(new RateLimit(0, 40, true), VALID_ANOMALY));
    }

    @Test
    @DisplayName("시간당 한도가 분당 한도보다 작으면 기동에 실패한다 — 분 한도가 죽은 설정이 된다")
    void rejectsHourLimitBelowMinuteLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReviewAbuseProperties(new RateLimit(10, 5, true), VALID_ANOMALY));
    }

    @Test
    @DisplayName("연속 제출 판정 간격·임계가 0 이하면 기동에 실패한다")
    void rejectsNonPositiveRapidSettings() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReviewAbuseProperties(VALID_LIMIT, new Anomaly(0, 3, 2)));
        assertThrows(IllegalArgumentException.class,
                () -> new ReviewAbuseProperties(VALID_LIMIT, new Anomaly(10, 0, 2)));
    }

    @Test
    @DisplayName("중복 문장 임계가 1이면 기동에 실패한다 — 모든 제출이 중복으로 표시된다")
    void rejectsDuplicateThresholdBelowTwo() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReviewAbuseProperties(VALID_LIMIT, new Anomaly(10, 3, 1)));
    }

    @Test
    @DisplayName("정상 설정은 그대로 통과한다")
    void acceptsValidConfiguration() {
        assertThatCode(() -> new ReviewAbuseProperties(VALID_LIMIT, VALID_ANOMALY))
                .doesNotThrowAnyException();
    }
}
