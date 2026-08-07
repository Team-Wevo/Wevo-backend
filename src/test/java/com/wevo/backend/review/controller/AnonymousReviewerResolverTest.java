package com.wevo.backend.review.controller;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 익명 검토자 키 쿠키의 속성 계약을 검증한다. (API_SPEC §3.5.3)
 *
 * <p>이 키는 중복 제출 차단(R002)과 {@code alreadySubmitted} 판정의 기준이라, 평문 HTTP 로
 * 새어 나가면 다른 검토자를 사칭할 수 있다. 속성이 빠져도 기능 테스트는 그대로 통과하므로
 * {@code Set-Cookie} 문자열 자체를 확인한다.
 */
class AnonymousReviewerResolverTest {

    private static final String VALID_KEY = "3f2504e0-4f89-41d3-9a0c-0305e82c3301";

    @Test
    @DisplayName("운영 설정에서는 Secure 를 붙여 HTTPS 요청에만 쿠키가 실린다")
    void issuedCookieIsSecureWhenEnabled() {
        String setCookie = issueCookie(new AnonymousReviewerResolver(true));

        assertThat(setCookie).contains("Secure");
    }

    @Test
    @DisplayName("로컬 설정에서는 Secure 를 빼 HTTP 에서도 쿠키가 저장된다")
    void issuedCookieOmitsSecureWhenDisabled() {
        // 로컬에서 Secure 를 켜면 브라우저가 쿠키를 저장하지 않아 매 요청 키가 새로 발급되고,
        // 중복 제출 차단이 무력화된 채로 개발이 진행된다.
        String setCookie = issueCookie(new AnonymousReviewerResolver(false));

        assertThat(setCookie).doesNotContain("Secure");
    }

    @Test
    @DisplayName("Secure 를 켜도 나머지 쿠키 속성은 그대로 유지된다")
    void issuedCookieKeepsOtherAttributes() {
        String setCookie = issueCookie(new AnonymousReviewerResolver(true));

        assertThat(setCookie)
                .startsWith(AnonymousReviewerResolver.COOKIE_NAME + "=")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                // 명세는 Path=/ 지만 이 키를 쓰는 API 가 /public/** 뿐이라 구현이 더 좁다.
                .contains("Path=/public")
                .contains("Max-Age=31536000");
    }

    @Test
    @DisplayName("이미 유효한 쿠키가 있으면 재발급하지 않는다")
    void existingCookieIsReused() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AnonymousReviewerResolver.COOKIE_NAME, VALID_KEY));
        MockHttpServletResponse response = new MockHttpServletResponse();

        String resolved = new AnonymousReviewerResolver(true).resolve(request, response);

        assertThat(resolved).isEqualTo(VALID_KEY);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    private String issueCookie(AnonymousReviewerResolver resolver) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        resolver.resolve(new MockHttpServletRequest(), response);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return setCookie;
    }
}
