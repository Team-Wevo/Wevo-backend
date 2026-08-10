package com.wevo.backend.review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 비인증 외부 검토 제출(`/public`)의 남용 방지 설정.
 *
 * <p>외부 검토자는 신원 보증이 없다 — 링크 토큰만 알면 누구나 제출할 수 있고, 익명 검토자 키는
 * 클라이언트가 보관하는 값이라 새로 만들면 "브라우저당 1회"({@code R002})를 우회할 수 있다.
 *
 * <p><b>IP 기준 값은 두지 않는다</b> — 제품 정책서 §6.2.4가 "IP 기준 제한은 사용하지 않는다
 * (학교·회사 공유망에서 정상 검토자 오탐 우려)"로 명시한다. 그래서 차단은 <b>링크 단위 속도</b>로만
 * 하고, 위조 흔적은 저장된 제출의 <b>시간 분포와 본문</b>에서 찾는다.
 *
 * @param rateLimit 제출 속도 제한 (링크 단위)
 * @param anomaly   이상 신호 판정 기준 (차단이 아니라 팀장 화면 표시용)
 */
@ConfigurationProperties(prefix = "app.review.abuse")
public record ReviewAbuseProperties(
        RateLimit rateLimit,
        Anomaly anomaly
) {

    public ReviewAbuseProperties {
        rateLimit = requireRateLimit(rateLimit);
        anomaly = requireAnomaly(anomaly);
    }

    /**
     * 고정 윈도 속도 제한값. <b>시도 기준</b>으로 센다 — 실패한 제출도 카운트해야 형식만 바꿔가며
     * 두드리는 자동화를 막을 수 있다.
     *
     * <p>축이 링크 하나뿐인 것은 의도된 제약이다(정책서 §6.2.4). 링크 축만으로는 20 슬롯을
     * 채우는 것 자체를 막지 못하고 <b>채우는 데 걸리는 시간을 늘릴 뿐</b>이며, 정책도 "완전 차단은
     * 목표가 아니다"라고 못 박고 있다. 못 막은 부분은 {@link Anomaly} 신호로 팀장이 알아본다.
     *
     * @param perLinkPerMinute 링크당 1분 허용 제출 시도 수
     * @param perLinkPerHour   링크당 1시간 허용 제출 시도 수
     * @param failOpen         Redis 장애 시 제출을 통과시킬지 여부. 기본 {@code true} —
     *                         외부 검토는 확정 조건에 포함되지 않는 보조 기능(정책서 §6.2)이라
     *                         카운터 저장소 장애로 정상 검토자를 막는 쪽이 손해가 크다.
     */
    public record RateLimit(
            int perLinkPerMinute,
            int perLinkPerHour,
            boolean failOpen
    ) {
    }

    /**
     * 이상 신호 판정 기준.
     *
     * <p>둘 다 <b>사람의 검토 행동과 자동화를 가르는 지점</b>을 본다 — 사람은 본문을 읽고 문장을
     * 쓰는 데 최소 수십 초가 걸리고, 서로 다른 사람이 똑같은 문장을 낼 이유가 없다.
     *
     * @param rapidIntervalSeconds     인접 제출 간격이 이 초 미만이면 "사람 속도가 아니다"로 센다
     * @param rapidPairThreshold       그런 인접 쌍이 이 수 이상이면 자동 제출을 의심한다
     *                                 (1~2건은 두 사람이 동시에 낸 우연일 수 있어 하한을 둔다)
     * @param duplicateSummaryThreshold 같은 문장이 이 수 이상 반복되면 의심한다
     */
    public record Anomaly(
            int rapidIntervalSeconds,
            int rapidPairThreshold,
            int duplicateSummaryThreshold
    ) {
    }

    private static RateLimit requireRateLimit(RateLimit rateLimit) {
        if (rateLimit == null) {
            throw new IllegalArgumentException("app.review.abuse.rate-limit 설정은 필수입니다.");
        }
        requirePositive("rate-limit.per-link-per-minute", rateLimit.perLinkPerMinute());
        requirePositive("rate-limit.per-link-per-hour", rateLimit.perLinkPerHour());
        if (rateLimit.perLinkPerHour() < rateLimit.perLinkPerMinute()) {
            // 시간 한도가 분 한도보다 작으면 분 한도는 영원히 도달할 수 없는 죽은 설정이 된다.
            throw new IllegalArgumentException(
                    "app.review.abuse.rate-limit 의 시간당 한도는 분당 한도 이상이어야 합니다.");
        }
        return rateLimit;
    }

    private static Anomaly requireAnomaly(Anomaly anomaly) {
        if (anomaly == null) {
            throw new IllegalArgumentException("app.review.abuse.anomaly 설정은 필수입니다.");
        }
        requirePositive("anomaly.rapid-interval-seconds", anomaly.rapidIntervalSeconds());
        requirePositive("anomaly.rapid-pair-threshold", anomaly.rapidPairThreshold());
        if (anomaly.duplicateSummaryThreshold() < 2) {
            // 1이면 제출 1건짜리 섹션도 전부 "중복"으로 표시돼 신호가 무의미해진다.
            throw new IllegalArgumentException(
                    "app.review.abuse.anomaly.duplicate-summary-threshold 는 2 이상이어야 합니다.");
        }
        return anomaly;
    }

    private static void requirePositive(String field, int value) {
        // 0 이하면 정상 제출까지 전부 막히거나(속도 제한) 모든 제출이 의심으로 표시된다(이상 신호).
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "app.review.abuse." + field + " 는 1 이상이어야 합니다.");
        }
    }
}
