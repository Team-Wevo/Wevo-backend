package com.wevo.backend.auth.service;

import com.wevo.backend.auth.dto.response.TokenResponse;
import com.wevo.backend.global.security.JwtProvider;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DevLoginServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private DevLoginService devLoginService;

    @Test
    @DisplayName("기존 사용자가 있으면 새로 만들지 않고 토큰을 발급한다")
    void devLogin_existingUser_doesNotCreate_andReturnsTokens() {
        User user = User.builder().email("dev@wevo.com").name("개발자").status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", 5L);

        given(userRepository.findByEmail("dev@wevo.com")).willReturn(Optional.of(user));
        given(jwtProvider.createAccessToken(5L)).willReturn("access");
        given(jwtProvider.createRefreshToken(eq(5L), anyString())).willReturn("refresh");
        given(jwtProvider.getRefreshTokenValidityMs()).willReturn(1_000L);

        TokenResponse response = devLoginService.devLogin("dev@wevo.com", "개발자");

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        verify(userRepository, never()).save(any());
        verify(refreshTokenService).save(eq(5L), anyString(), eq("refresh"), eq(1_000L));
    }

    @Test
    @DisplayName("사용자가 없으면 생성한 뒤 토큰을 발급한다")
    void devLogin_newUser_createsUser_andReturnsTokens() {
        given(userRepository.findByEmail("new@wevo.com")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 8L);
            return saved;
        });
        given(jwtProvider.createAccessToken(8L)).willReturn("access");
        given(jwtProvider.createRefreshToken(eq(8L), anyString())).willReturn("refresh");
        given(jwtProvider.getRefreshTokenValidityMs()).willReturn(2_000L);

        TokenResponse response = devLoginService.devLogin("new@wevo.com", "새 사용자");

        assertThat(response.accessToken()).isEqualTo("access");
        verify(userRepository).save(any(User.class));
        verify(refreshTokenService).save(eq(8L), anyString(), eq("refresh"), eq(2_000L));
    }
}
