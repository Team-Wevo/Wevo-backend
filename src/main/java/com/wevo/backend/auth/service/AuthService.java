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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;
import java.util.Objects;

/**
 * 소셜 로그인, 토큰 재발급, 로그아웃을 담당하는 서비스.
 *
 * <p>계정 식별은 {@code provider + providerUserId} 로 하며, 최초 로그인 시 별도 입력 없이
 * 제공자 프로필로 {@link User} 를 자동 생성한다. (제품 정책서 §1.1)
 */
@Service
public class AuthService {

    /** users 테이블의 이메일 유니크 제약 이름. (V1 마이그레이션) 소문자 비교용. */
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";

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
        // 조회와 저장이 같은 기준을 쓰도록 이메일을 먼저 정규화한다. provider 가 대소문자·앞뒤 공백을
        // 다르게 내려주면 사전 검사가 기존 사용자를 놓치고 DB 유니크 제약(uk_users_email)에서 터져
        // U002 대신 C003 이 나가기 때문이다. (API_SPEC §3.1.1)
        String email = normalizeEmail(info.email());

        // 이메일이 이미 다른 소셜 계정으로 가입돼 있으면 거부한다. (계정 식별은 provider+id 기준,
        // 이메일은 유니크 유지 → 동일 이메일의 타 제공자 가입은 자동 연결하지 않고 차단)
        if (email != null && userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = saveNewUser(info, email);

        authAccountRepository.save(AuthAccount.builder()
                .user(user)
                .provider(info.provider())
                .providerUserId(info.providerUserId())
                .build());

        return user;
    }

    /**
     * 신규 사용자를 저장한다. 사전 검사를 통과했더라도 동시 요청이 같은 이메일로 함께 들어오면
     * DB 유니크 제약에서 걸리므로, 그 경우도 계약된 {@code U002} 로 변환한다.
     * (사전 검사만으로 무결성을 보장하지 않는다 — {@code CLAUDE.md §5.8})
     *
     * <p>변환 대상은 <b>이메일 유니크 제약 위반뿐</b>이다. {@code name NOT NULL}·
     * {@code chk_users_status} 같은 다른 무결성 위반까지 삼키면, 예를 들어 소셜 제공자가 닉네임을
     * 주지 않아 저장이 실패한 경우에도 "이미 다른 방식으로 가입된 이메일입니다"가 표시된다.
     * 이메일 제약이 아닌 위반은 그대로 던져 전역 예외 처리에 맡긴다.
     */
    private User saveNewUser(OAuthUserInfo info, String email) {
        try {
            return userRepository.saveAndFlush(User.builder()
                    .name(info.name())
                    .email(email)
                    .profileImageUrl(info.profileImageUrl())
                    .status(UserStatus.ACTIVE)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            if (isEmailUniqueViolation(exception)) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
            throw exception;
        }
    }

    /**
     * 이메일 유니크 제약({@code uk_users_email}) 위반인지 판별한다.
     *
     * <p>드라이버·DB 구현에 따라 제약 이름이 최상위 메시지에 없고 원인 예외 체인에만 담기므로
     * 전체 메시지를 확인한다. 이름 비교는 대소문자를 구분하지 않는다.
     */
    private boolean isEmailUniqueViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null
                    && message.toLowerCase(Locale.ROOT).contains(EMAIL_UNIQUE_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 소셜 제공자가 내려준 이메일을 저장·조회 공통 형식으로 맞춘다.
     *
     * <p>앞뒤 공백을 제거하고 소문자로 통일한다. 이메일의 도메인부는 대소문자를 구분하지 않으며,
     * Wevo 는 계정을 {@code provider + providerUserId} 로 식별하고 이메일은 중복 가입 차단에만
     * 사용하므로 로컬부까지 함께 소문자로 다뤄도 계정이 잘못 합쳐질 위험이 없다.
     *
     * @return 정규화한 이메일. 값이 없거나 공백뿐이면 {@code null} (Kakao 등 미제공 사례)
     */
    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private TokenResponse issueTokens(Long userId) {
        String accessToken = jwtProvider.createAccessToken(userId);
        String refreshToken = jwtProvider.createRefreshToken(userId);
        refreshTokenService.save(userId, refreshToken, jwtProvider.getRefreshTokenValidityMs());
        return new TokenResponse(accessToken, refreshToken);
    }
}
