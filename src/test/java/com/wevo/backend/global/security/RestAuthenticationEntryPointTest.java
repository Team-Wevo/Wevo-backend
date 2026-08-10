package com.wevo.backend.global.security;

import com.wevo.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 401 응답이 실패 사유를 구분해 내려주는지 검증한다. (#222)
 *
 * <p>모두 {@code A001} 로 뭉치면 클라이언트가 "만료면 조용히 재발급, 그 밖에는 재로그인"을
 * 구분할 수 없다.
 */
class RestAuthenticationEntryPointTest {

    private final RestAuthenticationEntryPoint entryPoint =
            new RestAuthenticationEntryPoint(new ObjectMapper());

    @Test
    @DisplayName("토큰이 없으면 A001 — 검증할 것이 없다")
    void withoutToken_returnsUnauthorized() throws IOException {
        MockHttpServletResponse response = commence(new MockHttpServletRequest());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"A001\"");
    }

    @Test
    @DisplayName("만료된 토큰이면 A004 — 클라이언트가 재발급을 시도할 수 있다")
    void expiredToken_returnsExpiredCode() throws IOException {
        MockHttpServletResponse response = commence(requestWith(ErrorCode.EXPIRED_TOKEN));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"A004\"");
    }

    @Test
    @DisplayName("위조된 토큰이면 A003 — 재발급이 아니라 재로그인 대상이다")
    void invalidToken_returnsInvalidCode() throws IOException {
        MockHttpServletResponse response = commence(requestWith(ErrorCode.INVALID_TOKEN));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"A003\"");
    }

    @Test
    @DisplayName("401 이 아닌 코드가 실려 오면 무시하고 A001 로 응답한다")
    void nonUnauthorizedCode_isIgnored() throws IOException {
        // 상태(401)와 코드의 의미가 어긋난 응답이 나가지 않게 한다.
        MockHttpServletResponse response = commence(requestWith(ErrorCode.FORBIDDEN));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"A001\"");
    }

    private MockHttpServletRequest requestWith(ErrorCode errorCode) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtAuthenticationFilter.AUTHENTICATION_ERROR_CODE, errorCode);
        return request;
    }

    private MockHttpServletResponse commence(MockHttpServletRequest request) throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(request, response, new BadCredentialsException("no auth"));
        return response;
    }
}
