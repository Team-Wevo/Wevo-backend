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
 * <p>해석 우선순위:
 * <ol>
 *   <li>요청 헤더 {@code X-Anonymous-Reviewer-Id} — 프론트가 로컬 저장소에 보관한 키를 보낼 때.
 *       <b>UUID v4 형식이 아니면 400(C001)</b>로 거부한다 (클라이언트 계약 위반).</li>
 *   <li>쿠키 {@code wevo_reviewer_id} — 서버가 심어 브라우저가 자동으로 되돌려 보내는 키.
 *       형식이 깨진 쿠키는 오류가 아니라 <b>재발급</b>으로 처리한다 (서버 발급 값이므로).</li>
 *   <li>둘 다 없으면 새 키를 발급하고 쿠키로 내려준다.</li>
 * </ol>
 *
 * <p>쿠키 삭제·시크릿 모드로 키를 새로 만들면 재제출 가능
 * (IP 기준 제한은 학교·회사 공유망 오탐 때문에 쓰지 않는다.)
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
        String fromHeader = request.getHeader(HEADER_NAME);
        if (StringUtils.hasText(fromHeader)) {
            if (!UUID_V4.matcher(fromHeader).matches()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        List.of(new FieldError(HEADER_NAME, "UUID v4 형식이어야 합니다.")));
            }
            return fromHeader;
        }

        String fromCookie = readCookie(request);
        if (StringUtils.hasText(fromCookie) && UUID_V4.matcher(fromCookie).matches()) {
            return fromCookie;
        }

        String issued = UUID.randomUUID().toString();
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(issued).toString());
        return issued;
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
