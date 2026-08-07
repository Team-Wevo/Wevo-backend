package com.wevo.backend.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 초대 토큰 파생 규칙을 검증한다.
 *
 * <p>이 클래스가 지켜야 할 성질은 두 가지다 — <b>같은 프로젝트면 항상 같은 토큰</b>(멱등 반환
 * 계약이 여기에 걸려 있다)이고, <b>비밀키 없이는 계산할 수 없다</b>(DB 만 유출됐을 때의 방어선).
 */
class InviteTokenFactoryTest {

    private static final String SECRET = "test-invite-token-secret-that-is-long-enough-000000";

    private final InviteTokenFactory factory = new InviteTokenFactory(SECRET);

    @Test
    @DisplayName("같은 프로젝트는 몇 번을 계산해도 같은 토큰이 나온다")
    void tokenIsStableForSameProject() {
        // 초대 링크 생성이 멱등이라(API_SPEC §3.2.5) 재호출에서도 같은 URL 이 나와야 한다.
        assertThat(factory.tokenFor(10L)).isEqualTo(factory.tokenFor(10L));
    }

    @Test
    @DisplayName("프로젝트가 다르면 토큰도 다르다")
    void tokenDiffersBetweenProjects() {
        assertThat(factory.tokenFor(10L)).isNotEqualTo(factory.tokenFor(11L));
    }

    @Test
    @DisplayName("비밀키가 다르면 같은 프로젝트라도 다른 토큰이 나온다")
    void tokenDependsOnSecret() {
        // DB 에 남는 것은 해시뿐이라, 비밀키를 모르면 projectId 를 알아도 토큰을 되살릴 수 없다.
        InviteTokenFactory other =
                new InviteTokenFactory("another-invite-token-secret-long-enough-00000000");

        assertThat(other.tokenFor(10L)).isNotEqualTo(factory.tokenFor(10L));
    }

    @Test
    @DisplayName("토큰은 64자 hex 다 — token_hash 컬럼 길이와 맞물린다")
    void tokenIsHexOfFixedLength() {
        assertThat(factory.tokenFor(10L)).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("비밀키가 비어 있으면 기동에 실패한다")
    void blankSecretFailsFast() {
        // 기본값을 두면 누구나 계산 가능한 토큰이 조용히 발급된다. 시작 시점에 터뜨린다.
        assertThatThrownBy(() -> new InviteTokenFactory("  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INVITE_TOKEN_SECRET");
    }
}
