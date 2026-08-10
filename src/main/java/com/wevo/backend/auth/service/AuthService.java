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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

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
     *
     * <p><b>탈퇴한 계정은 재발급하지 않는다.</b> 회원 탈퇴는 소프트 삭제라(§3.3.3) 사용자 행이
     * 남아 있어 저장소 조회만으로는 걸러지지 않는다. 탈퇴 시 Refresh Token 을 폐기하지만
     * ({@code WithdrawnUserAuthCleaner}), 그 삭제와 이 재발급이 교차하면 검증을 통과한 뒤
     * 회전이 키를 되살려 탈퇴 계정이 새 토큰을 무한 갱신할 수 있다. 상태를 직접 확인해 그 창을 닫는다.
     *
     * <p>검증과 회전은 {@link RefreshTokenService#rotate} 한 번으로 <b>원자적으로</b> 처리한다.
     * 나눠서 하면 같은 토큰으로 동시에 재발급했을 때 양쪽 모두 성공한다.
     *
     * <p><b>탈퇴와의 경합에 사용자 행 잠금까지 걸지는 않는다</b> — {@code rotate} 는 저장된 해시가
     * 있고 일치할 때만 쓰기 때문에 <b>지워진 키를 되살릴 수 없다.</b> 예전 구현이 위험했던 건 검사 후
     * 무조건 {@code SET} 하는 구조라, 그 사이 탈퇴가 키를 지우면 저장이 키를 부활시켰기 때문이다.
     * 상태 검사가 낡은 값을 읽더라도 회전 자체가 막히므로, 토큰 재발급마다 사용자 행을 배타 잠금하는
     * 비용을 치를 이유가 없다. 여기서 상태 검사가 맡는 몫은 "탈퇴 계정이 남은 키로 세션을 이어가는
     * 것"을 한 겹 더 막는 것이다.
     *
     * <p>실패 사유(없음·재사용·탈퇴)를 모두 {@code A005} 로 돌려준다 — 클라이언트가 할 일은
     * 어느 경우든 재로그인 하나뿐이고, 코드를 나누면 "그 계정은 탈퇴했다"는 사실이 새어 나간다.
     * (§5.8 — 클라이언트가 분기할 필요가 없으면 코드를 쪼개지 않는다)
     */
    @Transactional(readOnly = true)
    public TokenResponse reissue(String refreshToken) {
        Long userId = jwtProvider.parseUserId(refreshToken, TokenType.REFRESH);
        requireActiveUser(userId);

        String accessToken = jwtProvider.createAccessToken(userId);
        String newRefreshToken = jwtProvider.createRefreshToken(userId);
        RefreshTokenService.RotationResult result = refreshTokenService.rotate(
                userId, refreshToken, newRefreshToken, jwtProvider.getRefreshTokenValidityMs());

        if (result == RefreshTokenService.RotationResult.REUSE_DETECTED) {
            // 이미 회전된 토큰이 다시 왔다 = 유출 정황. 스크립트가 세션을 폐기했으므로 정상 사용자도
            // 재로그인해야 한다. 토큰 원문은 남기지 않는다(§7).
            log.warn("Refresh Token 재사용이 감지되어 세션을 폐기했습니다. userId={}", userId);
        }
        if (result != RefreshTokenService.RotationResult.ROTATED) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return new TokenResponse(accessToken, newRefreshToken);
    }

    /**
     * 재발급 대상이 살아 있는 계정인지 확인한다.
     *
     * <p>탈퇴했거나 아예 없는 계정이면 남아 있을 수 있는 Refresh Token 도 함께 지운다 — 쓸 수 없는
     * 계정의 키를 최대 14일 방치하지 않기 위해서다. 두 경우를 같이 처리하는 이유는 "재발급할 수 없는
     * 계정"이라는 점이 같아서다.
     */
    private void requireActiveUser(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.isWithdrawn()) {
            refreshTokenService.delete(userId);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
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
