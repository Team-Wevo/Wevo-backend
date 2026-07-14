package com.wevo.backend.review.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 외부 검토자의 <b>익명 검토자 키</b>를 해석·발급한다. (브라우저당 1회 판별의 기준)
 *
 * <p>해석 우선순위:
 * <ol>
 *   <li>요청 헤더 {@code X-Anonymous-Reviewer-Id} — 프론트가 로컬 저장소에 보관한 키를 보낼 때 </li>
 *   <li>쿠키 {@code wevo_reviewer_id} — 서버가 심어 브라우저가 자동으로 되돌려 보내는 키</li>
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

    /**
     * 요청에서 익명 검토자 키를 해석한다. 없으면 새로 발급해 응답 쿠키로 심는다.
     *
     * @return 항상 유효한 익명 검토자 키
     */
    public String resolve(HttpServletRequest request, HttpServletResponse response) {
        String fromHeader = request.getHeader(HEADER_NAME);
        if (StringUtils.hasText(fromHeader)) {
            return fromHeader;
        }

        String fromCookie = readCookie(request);
        if (StringUtils.hasText(fromCookie)) {
            return fromCookie;
        }

        String issued = UUID.randomUUID().toString().replace("-", "");
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

    private ResponseCookie buildCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .path("/public")
                .maxAge(COOKIE_MAX_AGE)
                .sameSite("Lax")
                .build();
    }
}
