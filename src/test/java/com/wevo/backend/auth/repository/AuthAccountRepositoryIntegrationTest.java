package com.wevo.backend.auth.repository;

import com.wevo.backend.auth.domain.AuthAccount;
import com.wevo.backend.auth.domain.AuthProvider;
import com.wevo.backend.global.config.JpaAuditingConfig;
import com.wevo.backend.global.persistence.PostgresTestContainerConfig;
import com.wevo.backend.user.domain.User;
import com.wevo.backend.user.domain.UserStatus;
import com.wevo.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, PostgresTestContainerConfig.class})
class AuthAccountRepositoryIntegrationTest {

    @Autowired
    private AuthAccountRepository authAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("같은 provider + providerUserId 조합은 하나만 저장할 수 있다")
    void save_duplicateProviderIdentity_throwsDataIntegrityViolation() {
        User firstUser = userRepository.save(User.builder()
                .name("첫 사용자")
                .email("first@wevo.com")
                .status(UserStatus.ACTIVE)
                .build());
        User secondUser = userRepository.save(User.builder()
                .name("두번째 사용자")
                .email("second@wevo.com")
                .status(UserStatus.ACTIVE)
                .build());

        authAccountRepository.saveAndFlush(AuthAccount.builder()
                .user(firstUser)
                .provider(AuthProvider.GOOGLE)
                .providerUserId("google-123")
                .build());

        assertThatThrownBy(() -> authAccountRepository.saveAndFlush(AuthAccount.builder()
                .user(secondUser)
                .provider(AuthProvider.GOOGLE)
                .providerUserId("google-123")
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
