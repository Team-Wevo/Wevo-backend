package com.wevo.backend.auth.controller;

import com.wevo.backend.auth.domain.AuthProvider;
import com.wevo.backend.auth.dto.response.TokenResponse;
import com.wevo.backend.auth.service.AuthService;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.security.AuthPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("지원하지 않는 provider 값이면 A006 공통 응답을 반환한다")
    void login_withUnsupportedProvider_returnsA006() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "provider": "NAVER",
                                  "code": "code",
                                  "redirectUri": "http://localhost:3000/oauth/callback"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A006"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 로그인 제공자입니다."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("이미 다른 소셜 계정으로 가입된 이메일이면 409 U002 를 반환한다")
    void login_withDuplicateEmail_returns409U002() throws Exception {
        // 서비스가 던진 DUPLICATE_EMAIL 이 HTTP 계약(409 U002)으로 나가는지 확인한다. (API_SPEC §3.1.1)
        given(authService.login(AuthProvider.GOOGLE, "code", "http://localhost:3000/oauth/callback"))
                .willThrow(new BusinessException(ErrorCode.DUPLICATE_EMAIL));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "provider": "GOOGLE",
                                  "code": "code",
                                  "redirectUri": "http://localhost:3000/oauth/callback"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("U002"))
                .andExpect(jsonPath("$.message").value("이미 다른 방식으로 가입된 이메일입니다."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("인증 없이 로그아웃하면 A001 공통 응답을 반환한다")
    void logout_withoutAuthentication_returnsA001() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A001"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("인증된 사용자가 로그아웃하면 성공 응답을 반환한다")
    void logout_withAuthentication_returnsSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthPrincipal(7L), null, AuthorityUtils.NO_AUTHORITIES))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("LOGOUT_SUCCESS"))
                .andExpect(jsonPath("$.message").value("로그아웃되었습니다."))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(authService).logout(7L);
    }

    @Test
    @DisplayName("local 프로파일이 아니면 dev-login 엔드포인트는 노출되지 않는다")
    void devLogin_withoutLocalProfile_isNotMapped() throws Exception {
        mockMvc.perform(post("/api/auth/dev-login"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("토큰 재발급은 인증 없이 호출할 수 있다")
    void reissue_withoutAuthentication_returnsTokens() throws Exception {
        given(authService.reissue("refresh-token"))
                .willReturn(new TokenResponse("new-access", "new-refresh"));

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType("application/json")
                        .content("""
                                {
                                  "refreshToken": "refresh-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("TOKEN_REISSUED"))
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"));
    }
}
