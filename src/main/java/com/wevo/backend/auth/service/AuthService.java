package com.wevo.backend.auth.service;

import com.wevo.backend.auth.domain.AuthAccount;
import com.wevo.backend.auth.domain.AuthProvider;
import com.wevo.backend.auth.dto.response.TokenResponse;
import com.wevo.backend.auth.repository.AuthAccountRepository;
import com.wevo.backend.auth.service.oauth.OAuthClientRouter;
import com.wevo.backend.auth.service.oauth.OAuthUserInfo;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.global.security.JwtProvider;
import com.wevo.backend.global.security.TokenType;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

/**
 * 소셜 로그인, 토큰 재발급, 로그아웃을 담당하는 서비스.
 *
 * <p>계정 식별은 {@code provider + providerUserId} 로 하며, 최초 로그인 시 별도 입력 없이
 * 제공자 프로필로 {@link User} 를 자동 생성한다. (제품 정책서 §1.1)
 */
@Service
public class AuthService {

    private final OAuthClientRouter oAuthClientRouter;
    private final UserRepository userRepository;
    private final AuthAccountRepository authAccountRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final TransactionTemplate transactionTemplate;

    public AuthService(OAuthClientRouter oAuthClientRouter,
                       UserRepository userRepository,
                       AuthAccountRepository authAccountRepository,
                       JwtProvider jwtProvider,
                       RefreshTokenService refreshTokenService,
                       TransactionTemplate transactionTemplate) {
        this.oAuthClientRouter = oAuthClientRouter;
        this.userRepository = userRepository;
        this.authAccountRepository = authAccountRepository;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 소셜 인가코드로 로그인한다. 신규 사용자는 자동 가입 후 토큰을 발급한다.
     */
    public TokenResponse login(AuthProvider provider, String code, String redirectUri) {
        OAuthUserInfo userInfo = oAuthClientRouter.getClient(provider)
                .fetchUserInfo(code, redirectUri);

        User user = Objects.requireNonNull(transactionTemplate.execute(status -> authAccountRepository
                .findByProviderAndProviderUserId(userInfo.provider(), userInfo.providerUserId())
                .map(AuthAccount::getUser)
                .orElseGet(() -> register(userInfo))));

        return issueTokens(user.getId());
    }

    /**
     * Refresh Token 을 검증하고 Access/Refresh 를 재발급한다. (회전 방식)
     */
    @Transactional(readOnly = true)
    public TokenResponse reissue(String refreshToken) {
        Long userId = jwtProvider.parseUserId(refreshToken, TokenType.REFRESH);
        if (!refreshTokenService.matches(userId, refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return issueTokens(userId);
    }

    /**
     * 저장된 Refresh Token 을 폐기한다.
     */
    public void logout(Long userId) {
        refreshTokenService.delete(userId);
    }

    private User register(OAuthUserInfo info) {
        // 이메일이 이미 다른 소셜 계정으로 가입돼 있으면 거부한다. (계정 식별은 provider+id 기준,
        // 이메일은 유니크 유지 → 동일 이메일의 타 제공자 가입은 자동 연결하지 않고 차단)
        if (info.email() != null && userRepository.findByEmail(info.email()).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = userRepository.save(User.builder()
                .name(info.name())
                .email(info.email())
                .profileImageUrl(info.profileImageUrl())
                .status(UserStatus.ACTIVE)
                .build());

        authAccountRepository.save(AuthAccount.builder()
                .user(user)
                .provider(info.provider())
                .providerUserId(info.providerUserId())
                .build());

        return user;
    }

    private TokenResponse issueTokens(Long userId) {
        String accessToken = jwtProvider.createAccessToken(userId);
        String refreshToken = jwtProvider.createRefreshToken(userId);
        refreshTokenService.save(userId, refreshToken, jwtProvider.getRefreshTokenValidityMs());
        return new TokenResponse(accessToken, refreshToken);
    }
}
