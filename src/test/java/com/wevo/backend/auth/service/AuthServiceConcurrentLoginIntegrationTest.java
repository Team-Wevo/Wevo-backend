package com.wevo.backend.auth.service;

import com.wevo.backend.auth.domain.AuthProvider;
import com.wevo.backend.auth.repository.AuthAccountRepository;
import com.wevo.backend.auth.service.oauth.OAuthClient;
import com.wevo.backend.auth.service.oauth.OAuthClientRouter;
import com.wevo.backend.auth.service.oauth.OAuthUserInfo;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 같은 소셜 계정의 <b>최초 로그인이 동시에</b> 들어올 때를 실제 PostgreSQL 로 검증한다. (#223)
 *
 * <p>단위 테스트({@code AuthServiceTest})는 저장소가 즉시 예외를 던지도록 모킹하므로 회복 분기만
 * 확인한다. 실제 제약 위반은 flush·커밋 시점에 터지고, 그때 트랜잭션은 롤백 표시가 된다 —
 * <b>재조회가 새 트랜잭션에서 이뤄지는지</b>는 실제 DB 로만 확인할 수 있다. ({@code CLAUDE.md §8})
 *
 * <p>특히 {@code login} 이 트랜잭션 밖에서 돈다는 전제에 기대고 있다. 누군가 클래스에
 * {@code @Transactional} 을 붙이면 재조회가 롤백 표시된 트랜잭션 안에서 돌아 깨지는데, 이 테스트가
 * 그 회귀를 잡는다.
 *
 * <p>Redis 는 이 시나리오의 대상이 아니라 토큰 저장만 모킹한다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestContainerConfig.class)
class AuthServiceConcurrentLoginIntegrationTest {

    private static final int CONCURRENCY = 8;

    @Autowired
    private AuthService authService;
    @Autowired
    private AuthAccountRepository authAccountRepository;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private OAuthClientRouter oAuthClientRouter;
    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @Test
    @DisplayName("이메일 없는 제공자의 동시 최초 로그인 — 전부 성공하고 계정은 하나만 생긴다")
    void concurrentFirstLoginWithoutEmail_createsSingleAccount() throws Exception {
        // Kakao 이메일 미동의 경로. users 이메일 제약이 없어 auth_accounts 유니크에서 갈린다.
        String providerUserId = "kakao-" + UUID.randomUUID();
        givenProvider(AuthProvider.KAKAO, new OAuthUserInfo(
                AuthProvider.KAKAO, providerUserId, null, "카카오유저", null));

        long usersBefore = userRepository.count();
        List<String> accessTokens = loginConcurrently(AuthProvider.KAKAO);

        assertThat(accessTokens)
                .as("늦게 도착한 요청도 방금 만들어진 본인 계정을 찾아 성공해야 한다")
                .hasSize(CONCURRENCY)
                .doesNotContainNull();
        assertThat(userRepository.count())
                .as("경합해도 사용자는 하나만 생긴다")
                .isEqualTo(usersBefore + 1);
        assertThat(authAccountRepository
                .findByProviderAndProviderUserId(AuthProvider.KAKAO, providerUserId))
                .isPresent();
    }

    @Test
    @DisplayName("이메일 있는 제공자의 동시 최초 로그인 — U002 없이 전부 성공한다")
    void concurrentFirstLoginWithEmail_doesNotReportDuplicateEmail() throws Exception {
        // Google 경로. users 이메일 유니크가 먼저 걸려 U002 로 변환되는데, 본인 계정이므로
        // "이미 다른 방식으로 가입된 이메일입니다"가 뜨면 안 된다.
        String providerUserId = "google-" + UUID.randomUUID();
        String email = providerUserId + "@wevo.com";
        givenProvider(AuthProvider.GOOGLE, new OAuthUserInfo(
                AuthProvider.GOOGLE, providerUserId, email, "홍길동", null));

        long usersBefore = userRepository.count();
        List<String> accessTokens = loginConcurrently(AuthProvider.GOOGLE);

        assertThat(accessTokens).hasSize(CONCURRENCY).doesNotContainNull();
        assertThat(userRepository.count()).isEqualTo(usersBefore + 1);
        assertThat(userRepository.findByEmail(email)).isPresent();
    }

    private void givenProvider(AuthProvider provider, OAuthUserInfo userInfo) {
        OAuthClient client = mock(OAuthClient.class);
        given(oAuthClientRouter.getClient(provider)).willReturn(client);
        given(client.fetchUserInfo("code", "uri")).willReturn(userInfo);
    }

    /**
     * 여러 스레드를 장벽에 모아 두었다가 한꺼번에 로그인시킨다. 순차로 흘러가면 경합 자체가
     * 일어나지 않아 회복 경로를 지나지 않는다.
     */
    private List<String> loginConcurrently(AuthProvider provider) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(CONCURRENCY);
        try (var executor = Executors.newFixedThreadPool(CONCURRENCY)) {
            List<Callable<String>> tasks = IntStream.range(0, CONCURRENCY)
                    .<Callable<String>>mapToObj(index -> () -> {
                        barrier.await();
                        return authService.login(provider, "code", "uri").accessToken();
                    })
                    .toList();
            return executor.invokeAll(tasks).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError(
                                    "동시 최초 로그인은 모두 성공해야 한다", exception);
                        }
                    })
                    .toList();
        }
    }
}
