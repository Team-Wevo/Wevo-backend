package com.wevo.backend.auth.service.oauth;

import com.wevo.backend.auth.domain.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleOAuthClientTest {

    private final GoogleOAuthClient client = new GoogleOAuthClient(new OAuthProperties(
            new OAuthProperties.Provider("client-id", "client-secret", "token-uri", "user-info-uri"),
            null,
            new OAuthProperties.Timeout(Duration.ofSeconds(3), Duration.ofSeconds(5))));

    @Test
    @DisplayName("이메일이 검증됐으면 sub/email/이름/사진을 모두 매핑한다")
    void mapToUserInfo_withVerifiedEmail_mapsAllFields() {
        Map<String, Object> body = Map.of(
                "sub", "google-123",
                "email", "user@wevo.com",
                "email_verified", true,
                "name", "홍길동",
                "picture", "http://img/google.png");

        OAuthUserInfo info = client.mapToUserInfo(body);

        assertThat(info.provider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(info.providerUserId()).isEqualTo("google-123");
        assertThat(info.email()).isEqualTo("user@wevo.com");
        assertThat(info.name()).isEqualTo("홍길동");
        assertThat(info.profileImageUrl()).isEqualTo("http://img/google.png");
    }

    @Test
    @DisplayName("이메일이 검증되지 않았으면 email 은 null 로 매핑한다 — 선점 방지")
    void mapToUserInfo_withUnverifiedEmail_mapsNullEmail() {
        // 이메일은 중복 가입 차단에 쓰인다. 검증되지 않은 값을 믿으면 타인의 이메일을 적어 둔 계정이
        // 먼저 가입해 실소유자의 가입을 영구히 막을 수 있다.
        Map<String, Object> body = Map.of(
                "sub", "google-777",
                "email", "victim@wevo.com",
                "email_verified", false,
                "name", "홍길동");

        OAuthUserInfo info = client.mapToUserInfo(body);

        assertThat(info.email()).isNull();
        // 계정 식별은 provider + sub 라 이메일이 비어도 로그인 자체는 된다.
        assertThat(info.providerUserId()).isEqualTo("google-777");
        assertThat(info.name()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("검증 여부 필드가 아예 없으면 email 은 null 로 매핑한다")
    void mapToUserInfo_withoutVerificationFlag_mapsNullEmail() {
        Map<String, Object> body = Map.of(
                "sub", "google-888",
                "email", "user@wevo.com",
                "name", "홍길동");

        OAuthUserInfo info = client.mapToUserInfo(body);

        assertThat(info.email()).isNull();
    }

    @Test
    @DisplayName("이메일 자체가 없어도 sub 만으로 안전하게 매핑한다")
    void mapToUserInfo_withoutEmail_mapsNullEmail() {
        Map<String, Object> body = new HashMap<>();
        body.put("sub", "google-999");

        OAuthUserInfo info = client.mapToUserInfo(body);

        assertThat(info.providerUserId()).isEqualTo("google-999");
        assertThat(info.email()).isNull();
        assertThat(info.name()).isNull();
        assertThat(info.profileImageUrl()).isNull();
    }
}
