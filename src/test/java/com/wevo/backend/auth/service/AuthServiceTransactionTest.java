package com.wevo.backend.auth.service;

import com.wevo.backend.auth.domain.AuthAccount;
import com.wevo.backend.auth.domain.AuthProvider;
import com.wevo.backend.auth.repository.AuthAccountRepository;
import com.wevo.backend.auth.service.oauth.OAuthClient;
import com.wevo.backend.auth.service.oauth.OAuthClientRouter;
import com.wevo.backend.auth.service.oauth.OAuthUserInfo;
import com.wevo.backend.global.security.JwtProvider;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(AuthServiceTransactionTest.TestConfig.class)
class AuthServiceTransactionTest {

    @Autowired
    private AuthService authService;
    @Autowired
    private OAuthClientRouter oAuthClientRouter;
    @Autowired
    private OAuthClient oAuthClient;
    @Autowired
    private AuthAccountRepository authAccountRepository;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private RefreshTokenService refreshTokenService;

    @Test
    @DisplayName("OAuth 사용자 정보 조회는 트랜잭션 없이 실행한다")
    void loginFetchesOAuthUserInfoOutsideTransaction() {
        Long userId = 10L;
        AtomicBoolean transactionActiveDuringFetch = new AtomicBoolean(true);

        User user = User.builder()
                .name("홍길동")
                .email("user@wevo.com")
                .status(UserStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);

        AuthAccount authAccount = AuthAccount.builder()
                .user(user)
                .provider(AuthProvider.GOOGLE)
                .providerUserId("google-123")
                .build();

        given(oAuthClientRouter.getClient(AuthProvider.GOOGLE)).willReturn(oAuthClient);
        given(oAuthClient.fetchUserInfo("code", "uri")).willAnswer(invocation -> {
            transactionActiveDuringFetch.set(TransactionSynchronizationManager.isActualTransactionActive());
            return new OAuthUserInfo(
                    AuthProvider.GOOGLE,
                    "google-123",
                    "user@wevo.com",
                    "홍길동",
                    "http://img"
            );
        });
        given(authAccountRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-123"))
                .willReturn(Optional.of(authAccount));
        given(jwtProvider.createAccessToken(userId)).willReturn("access");
        given(jwtProvider.createRefreshToken(eq(userId), anyString())).willReturn("refresh");
        given(jwtProvider.getRefreshTokenValidityMs()).willReturn(1_000L);

        authService.login(AuthProvider.GOOGLE, "code", "uri");

        assertThat(transactionActiveDuringFetch.get()).isFalse();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:auth-service-tx;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        @Bean
        OAuthClientRouter oAuthClientRouter() {
            return mock(OAuthClientRouter.class);
        }

        @Bean
        OAuthClient oAuthClient() {
            return mock(OAuthClient.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        AuthAccountRepository authAccountRepository() {
            return mock(AuthAccountRepository.class);
        }

        @Bean
        JwtProvider jwtProvider() {
            return mock(JwtProvider.class);
        }

        @Bean
        RefreshTokenService refreshTokenService() {
            return mock(RefreshTokenService.class);
        }

        @Bean
        AuthService authService(
                OAuthClientRouter oAuthClientRouter,
                UserRepository userRepository,
                AuthAccountRepository authAccountRepository,
                JwtProvider jwtProvider,
                RefreshTokenService refreshTokenService,
                TransactionTemplate transactionTemplate
        ) {
            return new AuthService(
                    oAuthClientRouter,
                    userRepository,
                    authAccountRepository,
                    jwtProvider,
                    refreshTokenService,
                    transactionTemplate
            );
        }
    }
}
