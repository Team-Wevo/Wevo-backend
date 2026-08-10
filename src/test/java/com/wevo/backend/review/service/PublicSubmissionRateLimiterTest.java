package com.wevo.backend.review.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.review.config.ReviewAbuseProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PublicSubmissionRateLimiterTest {

    private static final Long LINK_ID = 4242L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("한도 안이면 통과시킨다")
    void allowsWhenWithinLimits() {
        givenScriptResult(0L);

        assertThatCode(() -> limiter(true).checkSubmission(LINK_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("한도를 넘으면 R015 로 거절한다")
    void rejectsWhenLimitExceeded() {
        givenScriptResult(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> limiter(true).checkSubmission(LINK_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_SUBMISSION_RATE_LIMITED);
    }

    @Test
    @DisplayName("링크 축의 분·시간 두 윈도만 센다 — 출처(IP) 축은 두지 않는다 (정책서 §6.2.4)")
    void countsOnlyLinkWindows() {
        givenScriptResult(0L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);

        limiter(true).checkSubmission(LINK_ID);

        then(redisTemplate).should()
                .execute(any(RedisScript.class), keys.capture(), any(Object[].class));
        assertThat(keys.getValue())
                .hasSize(2)
                // 키는 실재가 확인된 링크의 ID 로만 만든다 — 토큰(또는 그 해시)을 키로 쓰면
                // 아무 문자열이나 보내는 요청마다 새 키가 생겨 카운터가 메모리 소모 수단이 된다.
                .allMatch(key -> key.contains(":link:" + LINK_ID + ":"))
                .noneMatch(key -> key.contains(":ip:"));
    }

    @Test
    @DisplayName("각 키에 그 윈도의 한도와 TTL 이 짝지어 전달된다")
    void pairsEachKeyWithItsOwnLimitAndTtl() {
        // 키·한도·TTL 을 각각의 리스트로 만들면 순서가 어긋나도 실행돼, 분 한도가 시간 카운터에
        // 적용되는 조용한 오작동이 생긴다. Lua 는 ARGV[i]=한도, ARGV[#KEYS+i]=TTL 로 읽는다.
        givenScriptResult(0L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);

        limiter(true).checkSubmission(LINK_ID);

        then(redisTemplate).should()
                .execute(any(RedisScript.class), keys.capture(), args.capture());
        List<String> capturedKeys = keys.getValue();
        Object[] capturedArgs = args.getValue();
        assertThat(capturedArgs).hasSize(capturedKeys.size() * 2);

        for (int i = 0; i < capturedKeys.size(); i++) {
            boolean minuteWindow = capturedKeys.get(i).contains(":m:");
            assertThat(String.valueOf(capturedArgs[i]))
                    .isEqualTo(minuteWindow ? "10" : "40");
            assertThat(String.valueOf(capturedArgs[capturedKeys.size() + i]))
                    .isEqualTo(String.valueOf(minuteWindow
                            ? Duration.ofMinutes(2).toMillis()
                            : Duration.ofHours(2).toMillis()));
        }
    }

    @Test
    @DisplayName("Redis 장애 시 fail-open 이면 통과시킨다")
    void failsOpenWhenCounterUnavailable() {
        // 외부 검토는 확정 조건에 포함되지 않는 보조 기능이라, 카운터 장애로 정상 검토자를
        // 막는 손해가 더 크다는 팀 결정이다.
        givenScriptFailure();

        assertThatCode(() -> limiter(true).checkSubmission(LINK_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Redis 장애 시 fail-open 을 끄면 제출을 잠근다")
    void failsClosedWhenConfigured() {
        givenScriptFailure();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> limiter(false).checkSubmission(LINK_ID));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_SUBMISSION_RATE_LIMITED);
    }

    private void givenScriptResult(Long result) {
        given(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .willReturn(result);
    }

    private void givenScriptFailure() {
        given(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .willThrow(new QueryTimeoutException("redis down"));
    }

    private PublicSubmissionRateLimiter limiter(boolean failOpen) {
        return new PublicSubmissionRateLimiter(redisTemplate, new ReviewAbuseProperties(
                new ReviewAbuseProperties.RateLimit(10, 40, failOpen),
                new ReviewAbuseProperties.Anomaly(10, 3, 2)));
    }
}
