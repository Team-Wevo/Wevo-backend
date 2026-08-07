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

    /**
     * 이 사용자의 소셜 연결을 모두 끊는다. (회원 탈퇴)
     *
     * <p>연결을 남겨 두면 탈퇴자가 같은 소셜 계정으로 다시 로그인했을 때 이 행을 타고 예전 계정에
     * 그대로 붙어, 개인정보를 지운 채 <b>조용히 부활</b>한다. 끊어 두면 재로그인이 신규 가입으로
     * 처리돼 완전히 새 계정이 된다.
     */
    void deleteAllByUserId(Long userId);
}
