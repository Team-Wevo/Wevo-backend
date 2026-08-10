package com.wevo.backend.review.controller;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.response.FieldError;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 외부 검토자의 <b>익명 검토자 키</b>를 해석·발급한다. (브라우저당 1회 판별의 기준)
 *
 * <p>키 형식은 <b>UUID v4</b> 로 통일한다. (API_SPEC §3.5 — FE가 UUID v4를 생성해 헤더로 보낸다)
 *
 * <p>해석 우선순위 — <b>서버가 심은 쿠키가 1차, 클라이언트 헤더는 보조</b>다:
 * <ol>
 *   <li>쿠키 {@code wevo_reviewer_id} — 서버가 발급해 브라우저가 자동으로 되돌려 보내는 키.
 *       유효하면 <b>헤더를 보지 않고</b> 이 값을 쓴다. 형식이 깨진 쿠키는 오류가 아니라
 *       <b>재발급</b>으로 처리한다 (서버 발급 값이므로).</li>
 *   <li>요청 헤더 {@code X-Anonymous-Reviewer-Id} — 쿠키가 없을 때만 채택한다. 교차 출처에서
 *       쿠키를 쓸 수 없는 클라이언트를 위한 폴백이며, <b>UUID v4 형식이 아니면 400(C001)</b>로
 *       거부한다 (클라이언트 계약 위반). 채택한 값은 응답 쿠키로도 심어, 다음 요청부터는 서버가
 *       고정한 키가 우선하게 만든다.</li>
 *   <li>둘 다 없으면 새 키를 발급하고 쿠키로 내려준다.</li>
 * </ol>
 *
 * <p><b>왜 헤더를 강등했나</b> — 헤더가 쿠키를 이기면, 쿠키를 그대로 둔 채 매 요청 새 UUID 만
 * 헤더에 넣어도 매번 "새 브라우저"로 인정돼 중복 제출 차단({@code R002})이 무력화된다. 쿠키를
 * 먼저 보면 최소한 <b>쿠키를 정상적으로 저장하는 클라이언트</b>에서는 키를 바꿔치기할 수 없다.
 *
 * <p>다만 이것으로 위조 자체가 막히지는 않는다 — 쿠키를 아예 보내지 않는 스크립트는 여전히 매번
 * 새 키를 쓸 수 있다. 익명 검토는 본질적으로 신원 보증이 없는 설계이므로, 자동화된 대량 제출은
 * 신원이 아니라 속도로 막는다({@link com.wevo.backend.review.service.PublicSubmissionRateLimiter})
 * 그리고 결과 조회에 이상 신호를 함께 내려 팀장이 오염을 알아볼 수 있게 한다.
 */
@Component
public class AnonymousReviewerResolver {

    static final String COOKIE_NAME = "wevo_reviewer_id";
    static final String HEADER_NAME = "X-Anonymous-Reviewer-Id";
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(365);
    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private final boolean cookieSecure;

    /**
     * @param cookieSecure {@code Secure} 속성 적용 여부. 운영은 HTTPS 라 {@code true} 여야 하지만,
     *                     로컬은 HTTP 라 켜면 브라우저가 쿠키를 아예 저장하지 않아 검토자 키가 매 요청
     *                     새로 발급된다(중복 제출 차단이 무력화). 그래서 프로파일별 설정값으로 나눈다
     *                     — 기본값은 로컬 기준 {@code false} 이고 {@code prod} 에서 {@code true} 다.
     */
    public AnonymousReviewerResolver(
            @Value("${app.review.cookie-secure:false}") boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    /**
     * 요청에서 익명 검토자 키를 해석한다. 없으면 새로 발급해 응답 쿠키로 심는다.
     *
     * @return 항상 유효한 익명 검토자 키 (UUID v4)
     * @throws BusinessException 헤더 키가 UUID v4 형식이 아니면 {@link ErrorCode#INVALID_INPUT}
     */
    public String resolve(HttpServletRequest request, HttpServletResponse response) {
        // 헤더는 쿠키가 없을 때만 쓰지만, 형식 검증은 쿠키 유무와 무관하게 먼저 한다 —
        // 잘못된 헤더를 조용히 무시하면 클라이언트가 계약 위반을 눈치채지 못한다.
        String fromHeader = request.getHeader(HEADER_NAME);
        if (StringUtils.hasText(fromHeader) && !UUID_V4.matcher(fromHeader).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    List.of(new FieldError(HEADER_NAME, "UUID v4 형식이어야 합니다.")));
        }

        // 1차 — 서버가 심은 쿠키. 클라이언트가 헤더로 다른 키를 보내도 이 값을 이긴다.
        String fromCookie = readCookie(request);
        if (isValidKey(fromCookie)) {
            return fromCookie;
        }

        // 여기부터는 쓸 수 있는 쿠키가 없다는 뜻이므로, 어느 경로로 키를 정하든 **항상**
        // 응답 쿠키로 덮어쓴다. 형식이 깨진 쿠키를 그대로 두면 클라이언트가 매 요청 같은
        // 쓰레기 값을 다시 보내고, 그때마다 서버는 새 키를 만들어 중복 제출 판정이 흔들린다.
        String key = isValidKey(fromHeader) ? fromHeader : UUID.randomUUID().toString();
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(key).toString());
        return key;
    }

    /** 검토자 키로 쓸 수 있는 값인지. (형식 검증 규칙을 한 곳에 둔다) */
    private boolean isValidKey(String value) {
        return StringUtils.hasText(value) && UUID_V4.matcher(value).matches();
    }

    private String readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * 검토자 키 쿠키를 만든다. (API_SPEC §3.5.3)
     *
     * <p>이 키가 중복 제출 차단({@code R002})과 {@code alreadySubmitted} 판정의 기준이라, 평문
     * HTTP 로 새어 나가면 다른 검토자를 사칭할 수 있다. {@code Secure} 는 그래서 붙인다.
     *
     * <p>{@code Path} 는 명세의 {@code /} 가 아니라 {@code /public} 이다 — 이 키를 쓰는 API 가
     * {@code /public/**} 뿐이라 더 좁혀도 기능이 같고, 다른 경로 요청에 실려 나가지 않는다.
     */
    private ResponseCookie buildCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/public")
                .maxAge(COOKIE_MAX_AGE)
                .sameSite("Lax")
                .build();
    }
}
