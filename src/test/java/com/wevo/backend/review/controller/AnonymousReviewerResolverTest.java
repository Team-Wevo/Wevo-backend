package com.wevo.backend.review.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
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
    private static final String OTHER_KEY = "9f8b1c2d-3e4f-4a5b-8c9d-0a1b2c3d4e5f";

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

    @Test
    @DisplayName("서버가 심은 쿠키가 클라이언트 헤더를 이긴다")
    void cookieWinsOverHeader() {
        // 헤더가 쿠키를 이기면, 쿠키를 그대로 둔 채 매 요청 새 UUID 만 헤더에 넣어도 매번
        // "새 브라우저"로 인정돼 중복 제출 차단(R002)이 무력화된다.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AnonymousReviewerResolver.COOKIE_NAME, VALID_KEY));
        request.addHeader(AnonymousReviewerResolver.HEADER_NAME, OTHER_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String resolved = new AnonymousReviewerResolver(true).resolve(request, response);

        assertThat(resolved).isEqualTo(VALID_KEY);
    }

    @Test
    @DisplayName("쿠키가 없을 때만 헤더 키를 채택하고, 그 키를 쿠키로 고정한다")
    void headerIsAcceptedOnlyWithoutCookieAndIsPinned() {
        // 쿠키를 쓸 수 없는 교차 출처 클라이언트를 위한 폴백이다. 채택한 값을 쿠키로 심어야
        // 다음 요청부터는 서버가 고정한 키가 우선한다.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AnonymousReviewerResolver.HEADER_NAME, OTHER_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String resolved = new AnonymousReviewerResolver(true).resolve(request, response);

        assertThat(resolved).isEqualTo(OTHER_KEY);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains(AnonymousReviewerResolver.COOKIE_NAME + "=" + OTHER_KEY);
    }

    @Test
    @DisplayName("형식이 깨진 쿠키는 새 키를 발급해 반드시 덮어쓴다")
    void malformedCookieIsAlwaysOverwritten() {
        // 덮어쓰지 않으면 클라이언트가 매 요청 같은 쓰레기 값을 다시 보내고, 그때마다 서버가
        // 새 키를 만들어 중복 제출 판정(R002)이 흔들린다.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AnonymousReviewerResolver.COOKIE_NAME, "tampered-value"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        String resolved = new AnonymousReviewerResolver(true).resolve(request, response);

        assertThat(resolved).isNotEqualTo("tampered-value");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .isNotNull()
                .contains(AnonymousReviewerResolver.COOKIE_NAME + "=" + resolved);
    }

    @Test
    @DisplayName("형식이 깨진 쿠키 + 유효한 헤더면 헤더 키를 채택하고 쿠키를 덮어쓴다")
    void malformedCookieFallsBackToHeaderAndPinsIt() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AnonymousReviewerResolver.COOKIE_NAME, "tampered-value"));
        request.addHeader(AnonymousReviewerResolver.HEADER_NAME, OTHER_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String resolved = new AnonymousReviewerResolver(true).resolve(request, response);

        assertThat(resolved).isEqualTo(OTHER_KEY);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains(AnonymousReviewerResolver.COOKIE_NAME + "=" + OTHER_KEY);
    }

    @Test
    @DisplayName("형식이 깨진 헤더는 쿠키가 있어도 400(C001)으로 거절한다")
    void malformedHeaderIsRejectedEvenWithValidCookie() {
        // 조용히 무시하면 클라이언트가 계약 위반을 눈치채지 못한 채 잘못된 키를 계속 보낸다.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AnonymousReviewerResolver.COOKIE_NAME, VALID_KEY));
        request.addHeader(AnonymousReviewerResolver.HEADER_NAME, "not-a-uuid");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new AnonymousReviewerResolver(true)
                        .resolve(request, new MockHttpServletResponse()));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private String issueCookie(AnonymousReviewerResolver resolver) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        resolver.resolve(new MockHttpServletRequest(), response);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return setCookie;
    }
}
