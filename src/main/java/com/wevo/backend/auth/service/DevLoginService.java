package com.wevo.backend.auth.service;

import com.wevo.backend.auth.dto.response.TokenResponse;
import com.wevo.backend.global.security.JwtProvider;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개발 편의용 임시 로그인. 실제 소셜 인증(OAuth) 없이 지정한 이메일의 사용자로 JWT 를 발급한다.
 *
 * <p><b>local 프로파일에서만 활성화된다.</b> 운영 환경에서는 빈이 등록되지 않아 노출되지 않는다.
 * 프론트엔드·백엔드가 실제 Google/Kakao 자격증명 없이 보호 API 를 테스트하기 위한 용도다.
 *
 * <p>토큰 발급 절차는 {@link AuthService} 와 동일하며, 개발 전용 코드로 격리해 두어 손쉽게 제거할 수 있다.
 */
@Service
@Profile("local")
public class DevLoginService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    public DevLoginService(UserRepository userRepository,
                           JwtProvider jwtProvider,
                           RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * 이메일로 사용자를 찾고(없으면 생성) JWT 를 발급한다.
     */
    @Transactional
    public TokenResponse devLogin(String email, String name) {
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .name(name)
                        .status(UserStatus.ACTIVE)
                        .build()));

        Long userId = user.getId();
        String accessToken = jwtProvider.createAccessToken(userId);
        String refreshToken = jwtProvider.createRefreshToken(userId);
        refreshTokenService.save(userId, refreshToken, jwtProvider.getRefreshTokenValidityMs());
        return new TokenResponse(accessToken, refreshToken);
    }
}
