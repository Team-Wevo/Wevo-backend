package com.wevo.backend.global.security;

import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("만료된 토큰이면 사유를 요청 속성에 남겨 EntryPoint 가 A004 를 내려줄 수 있게 한다")
    void expiredToken_recordsErrorCode() throws Exception {
        MockHttpServletRequest request = requestWithToken("expired");
        given(jwtProvider.parseUserId("expired", TokenType.ACCESS))
                .willThrow(new BusinessException(ErrorCode.EXPIRED_TOKEN));

        doFilter(request);

        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTHENTICATION_ERROR_CODE))
                .isEqualTo(ErrorCode.EXPIRED_TOKEN);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("유효한 토큰이면 사유를 남기지 않는다")
    void validToken_doesNotRecordErrorCode() throws Exception {
        MockHttpServletRequest request = requestWithToken("valid");
        given(jwtProvider.parseUserId("valid", TokenType.ACCESS)).willReturn(7L);

        doFilter(request);

        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTHENTICATION_ERROR_CODE)).isNull();
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private void doFilter(MockHttpServletRequest request) throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        new JwtAuthenticationFilter(jwtProvider)
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }
}
