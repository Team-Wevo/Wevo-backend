package com.wevo.backend.review.service;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.review.config.ReviewAbuseProperties;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 비인증 외부 검토 제출의 <b>속도 제한</b>. (링크 단위 고정 윈도)
 *
 * <p>익명 검토자 키는 클라이언트가 만들어 보내는 값이라 신원 증명이 아니다 — 매 요청 새 키를 넣어
 * 루프를 돌리면 "브라우저당 1회"({@code R002})가 무력화되고, 링크당 20 슬롯이 위조 제출로 채워져
 * <b>정상 검토자가 제출하지 못하고 팀장 집계가 오염</b>된다. 신원으로 막을 수 없는 공격이므로
 * "얼마나 빨리 오는가"로 막는다. 카운트는 <b>시도 기준</b>이라 실패한 제출도 포함한다.
 *
 * <p><b>출처(IP) 축은 두지 않는다</b> — 정책서 §6.2.4가 "IP 기준 제한은 사용하지 않는다(학교·회사
 * 공유망에서 정상 검토자 오탐 우려)"로 배제한다. 그래서 이 제한은 20 슬롯이 채워지는 것 자체를
 * 막지 못하고 <b>채우는 데 걸리는 시간을 늘릴 뿐</b>이다. 정책도 "완전 차단은 목표가 아니다"라고
 * 명시하며, 남는 부분은 결과 조회의 이상 신호
 * ({@link com.wevo.backend.review.dto.response.SubmissionAnomalyResponse})가 팀장에게 알린다.
 *
 * <p>윈도는 Redis 고정 윈도(분·시간)다. 슬라이딩 윈도가 경계에서 더 정확하지만, 여기서 막으려는
 * 것은 초당 수십 건짜리 자동화라 고정 윈도로 충분하고 연산이 원자적(INCR+PEXPIRE 한 번)이다.
 *
 * <p><b>키는 실재하는 링크의 ID 로만 만든다</b> — 호출측이 링크를 식별한 뒤에 부르기 때문에,
 * 존재하지 않는 토큰을 무한히 바꿔 보내도 새 키가 생기지 않는다. 토큰(또는 그 해시)을 키로 쓰면
 * 카운터 자체가 메모리 소모 수단이 된다.
 *
 * <p><b>저장소 장애 시 동작</b>은 {@link ReviewAbuseProperties.RateLimit#failOpen} 이 정한다 —
 * 기본은 통과(fail-open)이며, 이 결정과 근거는 설정 문서에 적혀 있다.
 */
@Component
public class PublicSubmissionRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(PublicSubmissionRateLimiter.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter MINUTE_WINDOW = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter HOUR_WINDOW = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final String KEY_PREFIX = "review:submit:rate:";

    /**
     * 여러 카운터를 한 번에 올리고 <b>하나라도 한도를 넘으면 전부 되돌린다</b>.
     *
     * <p>되돌리지 않으면 이미 거절된 요청이 다른 축의 카운터를 계속 올려, 한 축이 막힌 동안
     * 무관한 다른 링크·출처까지 조기에 잠긴다.
     */
    private static final DefaultRedisScript<Long> COUNT_SCRIPT = countScript("""
            for i = 1, #KEYS do
              local current = tonumber(redis.call('GET', KEYS[i]) or '0')
              if current + 1 > tonumber(ARGV[i]) then
                for j = 1, i - 1 do redis.call('DECR', KEYS[j]) end
                return i
              end
              redis.call('INCR', KEYS[i])
              redis.call('PEXPIRE', KEYS[i], tonumber(ARGV[#KEYS + i]), 'NX')
            end
            return 0
            """);

    private final StringRedisTemplate redisTemplate;
    private final ReviewAbuseProperties properties;

    public PublicSubmissionRateLimiter(StringRedisTemplate redisTemplate,
                                       ReviewAbuseProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 이번 제출 시도를 세고, 한도를 넘으면 거절한다.
     *
     * <p><b>호출 시점은 링크를 식별한 뒤여야 한다.</b> 식별 전에 부르면 아무 문자열이나 토큰으로
     * 보내는 요청마다 새 키가 생겨 Redis 메모리가 공격자 마음대로 늘어난다. 그래서 키도 토큰이
     * 아니라 이미 조회한 행의 ID 를 받는다.
     *
     * @param linkId 실재가 확인된 검토 링크의 ID
     * @throws BusinessException 한도 초과면 {@link ErrorCode#REVIEW_SUBMISSION_RATE_LIMITED}({@code R015})
     */
    public void checkSubmission(Long linkId) {
        String linkKey = String.valueOf(linkId);
        ReviewAbuseProperties.RateLimit limits = properties.rateLimit();
        ZonedDateTime now = ZonedDateTime.now(KST);

        // 키·한도·TTL 을 한 묶음으로 만든다. 세 값을 각각의 리스트에 순서대로 넣으면 항목을
        // 추가·삭제할 때 순서가 어긋나도 컴파일·실행이 되어, 분 한도가 시간 카운터에 적용되는
        // 식의 조용한 오작동이 생긴다. 묶어 두면 그런 어긋남이 구조적으로 불가능하다.
        List<Counter> counters = List.of(
                Counter.perMinute(linkKey, MINUTE_WINDOW.format(now), limits.perLinkPerMinute()),
                Counter.perHour(linkKey, HOUR_WINDOW.format(now), limits.perLinkPerHour()));

        List<String> keys = counters.stream().map(Counter::key).toList();
        // Lua 는 한도 전체를 먼저 읽고 TTL 전체를 그 뒤에서 읽으므로(ARGV[i], ARGV[#KEYS + i])
        // 두 묶음을 같은 순서로 이어 붙인다.
        List<String> args = new ArrayList<>(counters.stream().map(Counter::limitArg).toList());
        args.addAll(counters.stream().map(Counter::ttlArg).toList());

        Long exceededAt;
        try {
            exceededAt = redisTemplate.execute(COUNT_SCRIPT, keys, args.toArray());
        } catch (DataAccessException | IllegalStateException e) {
            // 카운터 저장소 장애. 남용 차단보다 정상 검토자의 제출을 우선할지는 설정이 정한다.
            log.warn("외부 검토 제출 속도 제한 카운터 사용 불가 — failOpen={}, exceptionType={}",
                    limits.failOpen(), e.getClass().getSimpleName());
            if (limits.failOpen()) {
                return;
            }
            throw new BusinessException(ErrorCode.REVIEW_SUBMISSION_RATE_LIMITED);
        }

        if (exceededAt != null && exceededAt > 0) {
            // 어느 윈도가 걸렸는지는 로그로만 남긴다 — 응답으로 알려주면 공격자가 한도를 역산해
            // 그 아래로 속도를 맞출 수 있다. 링크 ID 는 토큰이 아니라 내부 식별자라 남겨도 무방하다.
            log.warn("외부 검토 제출 속도 제한 초과 — window={}, linkId={}",
                    exceededAt == 1 ? "MINUTE" : "HOUR", linkId);
            throw new BusinessException(ErrorCode.REVIEW_SUBMISSION_RATE_LIMITED);
        }
    }

    /**
     * 카운터 하나 — 키와 그 키에 적용할 한도·TTL 을 <b>같이</b> 들고 다닌다.
     *
     * <p>TTL 은 윈도 길이의 2배로 잡는다. 고정 윈도라 카운터는 다음 윈도로 넘어가면 쓰이지 않지만,
     * 넉넉히 두어 경계 시각에 카운터가 먼저 사라져 한도가 리셋되는 일이 없게 한다.
     *
     * @param key      Redis 키
     * @param limit    이 키에 적용할 한도
     * @param ttlMillis 키 만료 시간(ms)
     */
    private record Counter(String key, int limit, long ttlMillis) {

        private static Counter perMinute(String linkKey, String window, int limit) {
            return new Counter(key(linkKey, "m", window), limit, Duration.ofMinutes(2).toMillis());
        }

        private static Counter perHour(String linkKey, String window, int limit) {
            return new Counter(key(linkKey, "h", window), limit, Duration.ofHours(2).toMillis());
        }

        private static String key(String linkKey, String unit, String window) {
            return KEY_PREFIX + "link:" + linkKey + ":" + unit + ":" + window;
        }

        private String limitArg() {
            return String.valueOf(limit);
        }

        private String ttlArg() {
            return String.valueOf(ttlMillis);
        }
    }

    private static DefaultRedisScript<Long> countScript(String lua) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(Long.class);
        return script;
    }
}
