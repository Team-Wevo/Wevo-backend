package com.wevo.backend.auth.service;

import com.wevo.backend.auth.domain.AuthAccount;
import com.wevo.backend.auth.domain.AuthProvider;
import com.wevo.backend.auth.dto.response.TokenResponse;
import com.wevo.backend.auth.repository.AuthAccountRepository;
import com.wevo.backend.auth.service.oauth.OAuthClient;
import com.wevo.backend.auth.service.oauth.OAuthClientRouter;
import com.wevo.backend.auth.service.oauth.OAuthUserInfo;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.security.JwtProvider;
import com.wevo.backend.global.security.TokenType;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private OAuthClientRouter oAuthClientRouter;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthAccountRepository authAccountRepository;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private OAuthClient oAuthClient;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("기존 계정으로 로그인하면 새 User 를 만들지 않고 토큰을 발급한다")
    void login_existingAccount_doesNotCreateUser_andReturnsTokens() {
        Long userId = 10L;
        OAuthUserInfo userInfo =
                new OAuthUserInfo(AuthProvider.GOOGLE, "google-123", "user@wevo.com", "홍길동", "http://img");
        User user = User.builder()
                .name("홍길동").email("user@wevo.com").status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", userId);
        AuthAccount account = AuthAccount.builder()
                .user(user).provider(AuthProvider.GOOGLE).providerUserId("google-123").build();

        given(oAuthClientRouter.getClient(AuthProvider.GOOGLE)).willReturn(oAuthClient);
        given(oAuthClient.fetchUserInfo("code", "uri")).willReturn(userInfo);
        given(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-123"))
                .willReturn(Optional.of(account));
        given(jwtProvider.createAccessToken(userId)).willReturn("access");
        given(jwtProvider.createRefreshToken(userId)).willReturn("refresh");
        given(jwtProvider.getRefreshTokenValidityMs()).willReturn(1_000L);

        TokenResponse response = authService.login(AuthProvider.GOOGLE, "code", "uri");

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        verify(userRepository, never()).save(any());
        verify(refreshTokenService).save(userId, "refresh", 1_000L);
    }

    @Test
    @DisplayName("신규 계정으로 로그인하면 User 와 AuthAccount 를 생성하고 토큰을 발급한다")
    void login_newAccount_createsUserAndAuthAccount_andReturnsTokens() {
        Long newUserId = 55L;
        // 카카오는 이메일을 제공하지 않을 수 있어 email=null 로 가입되는 케이스
        OAuthUserInfo userInfo =
                new OAuthUserInfo(AuthProvider.KAKAO, "kakao-999", null, "닉네임", null);

        given(oAuthClientRouter.getClient(AuthProvider.KAKAO)).willReturn(oAuthClient);
        given(oAuthClient.fetchUserInfo("code", "uri")).willReturn(userInfo);
        given(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-999"))
                .willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", newUserId);
            return saved;
        });
        given(jwtProvider.createAccessToken(newUserId)).willReturn("access");
        given(jwtProvider.createRefreshToken(newUserId)).willReturn("refresh");
        given(jwtProvider.getRefreshTokenValidityMs()).willReturn(2_000L);

        TokenResponse response = authService.login(AuthProvider.KAKAO, "code", "uri");

        assertThat(response.accessToken()).isEqualTo("access");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(userCaptor.getValue().getEmail()).isNull();

        ArgumentCaptor<AuthAccount> accountCaptor = ArgumentCaptor.forClass(AuthAccount.class);
        verify(authAccountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getProvider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(accountCaptor.getValue().getProviderUserId()).isEqualTo("kakao-999");
        verify(refreshTokenService).save(newUserId, "refresh", 2_000L);
    }

    @Test
    @DisplayName("이미 가입된 이메일로 신규 소셜 계정이 로그인하면 DUPLICATE_EMAIL 예외를 던진다")
    void login_newAccountWithExistingEmail_throwsDuplicateEmail() {
        OAuthUserInfo userInfo =
                new OAuthUserInfo(AuthProvider.GOOGLE, "google-new", "dup@wevo.com", "홍길동", null);

        given(oAuthClientRouter.getClient(AuthProvider.GOOGLE)).willReturn(oAuthClient);
        given(oAuthClient.fetchUserInfo("code", "uri")).willReturn(userInfo);
        given(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-new"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("dup@wevo.com")).willReturn(
                Optional.of(User.builder().email("dup@wevo.com").status(UserStatus.ACTIVE).build()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(AuthProvider.GOOGLE, "code", "uri"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        verify(userRepository, never()).save(any());
        verify(authAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("저장된 토큰과 일치하는 Refresh Token 으로 재발급하면 새 토큰을 반환한다")
    void reissue_validToken_returnsNewTokens() {
        given(jwtProvider.parseUserId("refresh", TokenType.REFRESH)).willReturn(3L);
        given(refreshTokenService.matches(3L, "refresh")).willReturn(true);
        given(jwtProvider.createAccessToken(3L)).willReturn("newAccess");
        given(jwtProvider.createRefreshToken(3L)).willReturn("newRefresh");
        given(jwtProvider.getRefreshTokenValidityMs()).willReturn(1_000L);

        TokenResponse response = authService.reissue("refresh");

        assertThat(response.accessToken()).isEqualTo("newAccess");
        assertThat(response.refreshToken()).isEqualTo("newRefresh");
        verify(refreshTokenService).save(3L, "newRefresh", 1_000L);
    }

    @Test
    @DisplayName("저장된 토큰과 다른 Refresh Token 으로 재발급하면 INVALID_REFRESH_TOKEN 예외를 던진다")
    void reissue_mismatchedToken_throwsInvalidRefreshToken() {
        given(jwtProvider.parseUserId("refresh", TokenType.REFRESH)).willReturn(3L);
        given(refreshTokenService.matches(3L, "refresh")).willReturn(false);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> authService.reissue("refresh"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        verify(jwtProvider, never()).createAccessToken(anyLong());
    }

    @Test
    @DisplayName("로그아웃하면 저장된 Refresh Token 을 폐기한다")
    void logout_deletesRefreshToken() {
        authService.logout(9L);

        verify(refreshTokenService).delete(9L);
    }
}
