package com.wevo.backend.auth.repository;

import com.wevo.backend.auth.domain.AuthAccount;
import com.wevo.backend.auth.domain.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {

    /**
     * 계정 식별 기준(provider + providerUserId)으로 소셜 계정을 조회한다.
     */
    Optional<AuthAccount> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
