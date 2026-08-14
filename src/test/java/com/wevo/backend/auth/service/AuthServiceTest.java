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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
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
    private TransactionTemplate transactionTemplate;
    @Mock
    private OAuthClient oAuthClient;

    @InjectMocks
    private AuthService authService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
        });
    }

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
        verify(userRepository, never()).saveAndFlush(any());
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
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> {
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
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(userCaptor.getValue().getEmail()).isNull();

        ArgumentCaptor<AuthAccount> accountCaptor = ArgumentCaptor.forClass(AuthAccount.class);
        verify(authAccountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getProvider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(accountCaptor.getValue().getProviderUserId()).isEqualTo("kakao-999");
        verify(refreshTokenService).save(newUserId, "refresh", 2_000L);
    }

    @Test
    @DisplayName("제공자가 name 을 주지 않아도(이메일만) 신규 로그인이 실패하지 않고 이메일 앞부분을 표시명으로 저장한다")
    void login_newAccount_nullName_withEmail_defaultsDisplayNameFromEmail() {
        Long newUserId = 56L;
        // Google 이 profile 스코프를 요청하지 않아 name 이 없는 케이스 (#289)
        OAuthUserInfo userInfo =
                new OAuthUserInfo(AuthProvider.GOOGLE, "google-noname", "hoseok@wevo.com", null, null);

        given(oAuthClientRouter.getClient(AuthProvider.GOOGLE)).willReturn(oAuthClient);
        given(oAuthClient.fetchUserInfo("code", "uri")).willReturn(userInfo);
        given(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-noname"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("hoseok@wevo.com")).willReturn(Optional.empty());
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", newUserId);
            return saved;
        });
        given(jwtProvider.createAccessToken(newUserId)).willReturn("access");
        given(jwtProvider.createRefreshToken(newUserId)).willReturn("refresh");
        given(jwtProvider.getRefreshTokenValidityMs()).willReturn(2_000L);

        authService.login(AuthProvider.GOOGLE, "code", "uri");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getName()).isEqualTo("hoseok");
    }

    @Test
    @DisplayName("제공자가 name·email 을 모두 주지 않아도 기본 표시명으로 신규 로그인에 성공한다")
    void login_newAccount_nullNameAndEmail_defaultsDisplayName() {
        Long newUserId = 57L;
        // Kakao 에서 이메일·닉네임 동의를 모두 거부한 케이스 — name 도 email 도 null (#289)
        OAuthUserInfo userInfo =
                new OAuthUserInfo(AuthProvider.KAKAO, "kakao-noname", null, null, null);

        given(oAuthClientRouter.getClient(AuthProvider.KAKAO)).willReturn(oAuthClient);
        given(oAuthClient.fetchUserInfo("code", "uri")).willReturn(userInfo);
        given(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-noname"))
                .willReturn(Optional.empty());
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", newUserId);
            return saved;
        });
        given(jwtProvider.createAccessToken(newUserId)).willReturn("access");
        given(jwtProvider.createRefreshToken(newUserId)).willReturn("refresh");
        given(jwtProvider.getRefreshTokenValidityMs()).willReturn(2_000L);

        authService.login(AuthProvider.KAKAO, "code", "uri");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getName()).isEqualTo("사용자");
        assertThat(userCaptor.getValue().getEmail()).isNull();
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
    @DisplayName("대소문자·공백만 다른 이메일도 중복으로 보고 DUPLICATE_EMAIL 을 던진다")
    void login_existingEmailWithDifferentCase_throwsDuplicateEmail() {
        // provider 가 " DUP@Wevo.com " 처럼 내려줘도 저장된 소문자 이메일과 같은 계정으로 본다.
        OAuthUserInfo userInfo = new OAuthUserInfo(
                AuthProvider.KAKAO, "kakao-new", "  DUP@Wevo.com  ", "홍길동", null);

        given(oAuthClientRouter.getClient(AuthProvider.KAKAO)).willReturn(oAuthClient);
        given(oAuthClient.fetchUserInfo("code", "uri")).willReturn(userInfo);
        given(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-new"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("dup@wevo.com")).willReturn(
                Optional.of(User.builder().email("dup@wevo.com").status(UserStatus.ACTIVE).build()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(AuthProvider.KAKAO, "code", "uri"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        verify(userRepository, never()).saveAndFlush(any());
        verify(authAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("신규 가입 시 이메일을 소문자·공백 제거 형태로 저장한다")
    void login_newAccount_normalizesEmailBeforeSaving() {
        Long newUserId = 77L;
        OAuthUserInfo userInfo = new OAuthUserInfo(
                AuthProvider.GOOGLE, "google-fresh", " New.User@Wevo.COM ", "홍길동", null);

        given(oAuthClientRouter.getClient(AuthProvider.GOOGLE)).willReturn(oAuthClient);
        given(oAuthClient.fetchUserInfo("code", "uri")).willReturn(userInfo);
        given(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-fresh"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("new.user@wevo.com")).willReturn(Optional.empty());
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", newUserId);
            return saved;
        });
        given(jwtProvider.createAccessToken(newUserId)).willReturn("access");
        given(jwtProvider.createRefreshToken(newUserId)).willReturn("refresh");
        given(jwtProvider.getRefreshTokenValidityMs()).willReturn(2_000L);

        authService.login(AuthProvider.GOOGLE, "code", "uri");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("new.user@wevo.com");
    }

    @Test
    @DisplayName("동시 요청으로 이메일 유니크 제약이 깨지면 C003 이 아니라 DUPLICATE_EMAIL 로 변환한다")
    void login_concurrentDuplicateEmail_translatesConstraintViolation() {
        // 사전 검사를 통과한 뒤 다른 요청이 먼저 같은 이메일을 저장한 상황.
        // 변환하지 않으면 DataIntegrityViolationException 이 전역 핸들러에서 C003 으로 나간다.
        OAuthUserInfo userInfo = new OAuthUserInfo(
                AuthProvider.GOOGLE, "google-race", "race@wevo.com", "홍길동", null);

        given(oAuthClientRouter.getClient(AuthProvider.GOOGLE)).willReturn(oAuthClient);
        given(oAuthClient.fetchUserInfo("code", "uri")).willReturn(userInfo);
        given(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-race"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("race@wevo.com")).willReturn(Optional.empty());
        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(new DataIntegrityViolationException("uk_users_email"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(AuthProvider.GOOGLE, "code", "uri"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        verify(authAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("동시 최초 로그인으로 소셜 계정 유니크 제약이 깨지면 기존 계정을 찾아 멱등 성공한다")
    void login_concurrentFirstLogin_recoversIdempotently() {
        // 로그인 버튼 연타 등으로 같은 계정의 최초 로그인이 동시에 들어오면 양쪽 모두 "연결된 계정
        // 없음"을 보고 가입을 시도한다. 늦은 쪽이 제약에 걸리는 건 오류가 아니라 본인 계정이 방금
        // 만들어졌다는 뜻이다. (이메일을 주지 않는 제공자에서 나타나는 형태)
        OAuthUserInfo userInfo = new OAuthUserInfo(
                AuthProvider.KAKAO, "kakao-race", null, "카카오유저", null);
        User winner = User.builder().name("카카오유저").status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(winner, "id", 42L);

        given(oAuthClientRouter.getClient(AuthProvider.KAKAO)).willReturn(oAuthClient);
        given(oAuthClient.fetchUserInfo("code", "uri")).willReturn(userInfo);
        given(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-race"))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(AuthAccount.builder()
                        .user(winner).provider(AuthProvider.KAKAO).providerUserId("kakao-race").build()));
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(call -> call.getArgument(0));
        given(authAccountRepository.save(any(AuthAccount.class)))
                .willThrow(new DataIntegrityViolationException("uk_auth_accounts_provider_user"));
        given(jwtProvider.createAccessToken(42L)).willReturn("access");
        given(jwtProvider.createRefreshToken(42L)).willReturn("refresh");
        given(jwtProvider.getRefreshTokenValidityMs()).willReturn(1_000L);

        TokenResponse response = authService.login(AuthProvider.KAKAO, "code", "uri");

        assertThat(response.accessToken()).isEqualTo("access");
    }

    @Test
    @DisplayName("이메일 중복으로 실패했어도 내 소셜 계정이 생겼으면 멱등 성공으로 잇는다")
    void login_concurrentFirstLoginWithEmail_recoversIdempotently() {
        // 이메일을 주는 제공자에서는 users 유니크 제약이 먼저 걸려 U002 로 변환된다. 그대로 두면
        // 본인에게 "이미 다른 방식으로 가입된 이메일입니다"가 표시된다.
        OAuthUserInfo userInfo = new OAuthUserInfo(
                AuthProvider.GOOGLE, "google-race2", "me@wevo.com", "홍길동", null);
        User winner = User.builder().name("홍길동").email("me@wevo.com").status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(winner, "id", 43L);

        given(oAuthClientRouter.getClient(AuthProvider.GOOGLE)).willReturn(oAuthClient);
        given(oAuthClient.fetchUserInfo("code", "uri")).willReturn(userInfo);
        given(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-race2"))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(AuthAccount.builder()
                        .user(winner).provider(AuthProvider.GOOGLE).providerUserId("google-race2").build()));
        given(userRepository.findByEmail("me@wevo.com")).willReturn(Optional.empty());
        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(new DataIntegrityViolationException("uk_users_email"));
        given(jwtProvider.createAccessToken(43L)).willReturn("access");
        given(jwtProvider.createRefreshToken(43L)).willReturn("refresh");
        given(jwtProvider.getRefreshTokenValidityMs()).willReturn(1_000L);

        TokenResponse response = authService.login(AuthProvider.GOOGLE, "code", "uri");

        assertThat(response.accessToken()).isEqualTo("access");
    }

    @Test
    @DisplayName("이메일 제약이 아닌 무결성 위반은 DUPLICATE_EMAIL 로 바꾸지 않고 그대로 전파한다")
    void login_nonEmailConstraintViolation_isNotTranslated() {
        // 예: 소셜 제공자가 닉네임을 주지 않아 name NOT NULL 이 깨진 경우.
        // 이것까지 U002 로 삼키면 "이미 다른 방식으로 가입된 이메일입니다"가 잘못 표시된다.
        OAuthUserInfo userInfo = new OAuthUserInfo(
                AuthProvider.KAKAO, "kakao-noname", "noname@wevo.com", null, null);

        given(oAuthClientRouter.getClient(AuthProvider.KAKAO)).willReturn(oAuthClient);
        given(oAuthClient.fetchUserInfo("code", "uri")).willReturn(userInfo);
        given(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-noname"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("noname@wevo.com")).willReturn(Optional.empty());
        given(userRepository.saveAndFlush(any(User.class))).willThrow(
                new DataIntegrityViolationException(
                        "null value in column \"name\" violates not-null constraint"));

        assertThrows(DataIntegrityViolationException.class,
                () -> authService.login(AuthProvider.KAKAO, "code", "uri"));

        verify(authAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("제약 이름이 원인 예외에만 있어도 이메일 중복으로 판별한다")
    void login_constraintNameInCause_translatesToDuplicateEmail() {
        OAuthUserInfo userInfo = new OAuthUserInfo(
                AuthProvider.GOOGLE, "google-nested", "nested@wevo.com", "홍길동", null);

        given(oAuthClientRouter.getClient(AuthProvider.GOOGLE)).willReturn(oAuthClient);
        given(oAuthClient.fetchUserInfo("code", "uri")).willReturn(userInfo);
        given(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-nested"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("nested@wevo.com")).willReturn(Optional.empty());
        given(userRepository.saveAndFlush(any(User.class))).willThrow(
                new DataIntegrityViolationException(
                        "could not execute statement",
                        new IllegalStateException("ERROR: duplicate key value violates unique "
                                + "constraint \"UK_USERS_EMAIL\"")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(AuthProvider.GOOGLE, "code", "uri"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    @DisplayName("저장된 토큰과 일치하는 Refresh Token 으로 재발급하면 회전 후 새 토큰을 반환한다")
    void reissue_validToken_returnsNewTokens() {
        givenActiveUser(3L);
        given(jwtProvider.parseUserId("refresh", TokenType.REFRESH)).willReturn(3L);
        given(jwtProvider.createAccessToken(3L)).willReturn("newAccess");
        given(jwtProvider.createRefreshToken(3L)).willReturn("newRefresh");
        given(jwtProvider.getRefreshTokenValidityMs()).willReturn(1_000L);
        given(refreshTokenService.rotate(3L, "refresh", "newRefresh", 1_000L))
                .willReturn(RefreshTokenService.RotationResult.ROTATED);

        TokenResponse response = authService.reissue("refresh");

        assertThat(response.accessToken()).isEqualTo("newAccess");
        assertThat(response.refreshToken()).isEqualTo("newRefresh");
        // 검증과 저장이 rotate 한 번으로 원자 처리된다 — 별도 save 가 남아 있으면 경합 창이 생긴다.
        verify(refreshTokenService, never()).save(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("저장된 토큰이 없으면 INVALID_REFRESH_TOKEN 예외를 던진다")
    void reissue_missingToken_throwsInvalidRefreshToken() {
        givenActiveUser(3L);
        given(jwtProvider.parseUserId("refresh", TokenType.REFRESH)).willReturn(3L);
        given(jwtProvider.createAccessToken(3L)).willReturn("newAccess");
        given(jwtProvider.createRefreshToken(3L)).willReturn("newRefresh");
        given(jwtProvider.getRefreshTokenValidityMs()).willReturn(1_000L);
        given(refreshTokenService.rotate(3L, "refresh", "newRefresh", 1_000L))
                .willReturn(RefreshTokenService.RotationResult.NOT_FOUND);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> authService.reissue("refresh"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("이미 회전된 옛 Refresh Token 을 다시 쓰면 재사용으로 보고 거부한다")
    void reissue_reusedToken_throwsInvalidRefreshToken() {
        // 세션 폐기는 rotate 스크립트가 원자적으로 수행한다(RefreshTokenServiceTest 에서 검증).
        // 여기서는 재사용 결과가 성공으로 새지 않는지만 본다.
        givenActiveUser(3L);
        given(jwtProvider.parseUserId("refresh", TokenType.REFRESH)).willReturn(3L);
        given(jwtProvider.createAccessToken(3L)).willReturn("newAccess");
        given(jwtProvider.createRefreshToken(3L)).willReturn("newRefresh");
        given(jwtProvider.getRefreshTokenValidityMs()).willReturn(1_000L);
        given(refreshTokenService.rotate(3L, "refresh", "newRefresh", 1_000L))
                .willReturn(RefreshTokenService.RotationResult.REUSE_DETECTED);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> authService.reissue("refresh"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("탈퇴한 계정은 Refresh Token 이 남아 있어도 재발급하지 않고 그 토큰을 폐기한다")
    void reissue_withdrawnUser_throwsAndDiscardsToken() {
        // 탈퇴 시 토큰을 지우지만, 그 삭제와 재발급이 교차하면 회전이 키를 되살려 탈퇴 계정이
        // 세션을 무한 갱신할 수 있다. 상태 검사가 그 창을 닫는지 본다.
        User withdrawn = User.builder()
                .name(User.WITHDRAWN_NAME)
                .status(UserStatus.WITHDRAWN)
                .build();
        given(jwtProvider.parseUserId("refresh", TokenType.REFRESH)).willReturn(3L);
        given(userRepository.findById(3L)).willReturn(Optional.of(withdrawn));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> authService.reissue("refresh"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        verify(refreshTokenService).delete(3L);
        verify(refreshTokenService, never())
                .rotate(anyLong(), anyString(), anyString(), anyLong());
        verify(jwtProvider, never()).createAccessToken(anyLong());
    }

    @Test
    @DisplayName("토큰의 사용자가 없으면 재발급하지 않는다")
    void reissue_unknownUser_throwsInvalidRefreshToken() {
        given(jwtProvider.parseUserId("refresh", TokenType.REFRESH)).willReturn(3L);
        given(userRepository.findById(3L)).willReturn(Optional.empty());

        BusinessException exception =
                assertThrows(BusinessException.class, () -> authService.reissue("refresh"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        // 쓸 수 없는 계정의 키를 최대 14일 방치하지 않는다.
        verify(refreshTokenService).delete(3L);
        verify(refreshTokenService, never())
                .rotate(anyLong(), anyString(), anyString(), anyLong());
    }

    private void givenActiveUser(Long userId) {
        given(userRepository.findById(userId)).willReturn(Optional.of(User.builder()
                .name("호석")
                .status(UserStatus.ACTIVE)
                .build()));
    }

    @Test
    @DisplayName("로그아웃하면 저장된 Refresh Token 을 폐기한다")
    void logout_deletesRefreshToken() {
        authService.logout(9L);

        verify(refreshTokenService).delete(9L);
    }
}
