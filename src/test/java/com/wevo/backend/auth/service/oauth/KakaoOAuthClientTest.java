package com.wevo.backend.auth.service.oauth;

import com.wevo.backend.auth.domain.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoOAuthClientTest {

    private final KakaoOAuthClient client = new KakaoOAuthClient(new OAuthProperties(
            null,
            new OAuthProperties.Provider("client-id", "client-secret", "token-uri", "user-info-uri"),
            new OAuthProperties.Timeout(Duration.ofSeconds(3), Duration.ofSeconds(5))));

    @Test
    @DisplayName("전체 프로필이 있으면 id/email/닉네임/프로필이미지를 모두 매핑한다")
    void mapToUserInfo_withFullProfile_mapsAllFields() {
        Map<String, Object> profile = Map.of(
                "nickname", "카카오유저",
                "profile_image_url", "http://img/kakao.png");
        Map<String, Object> kakaoAccount = Map.of(
                "email", "user@kakao.com",
                "profile", profile);
        Map<String, Object> body = Map.of(
                "id", 123_456_789L,
                "kakao_account", kakaoAccount);

        OAuthUserInfo info = client.mapToUserInfo(body);

        assertThat(info.provider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(info.providerUserId()).isEqualTo("123456789");
        assertThat(info.email()).isEqualTo("user@kakao.com");
        assertThat(info.name()).isEqualTo("카카오유저");
        assertThat(info.profileImageUrl()).isEqualTo("http://img/kakao.png");
    }

    @Test
    @DisplayName("이메일 미동의(제공 안 됨) 시 email 은 null 로 매핑된다")
    void mapToUserInfo_withoutEmail_mapsNullEmail() {
        Map<String, Object> profile = Map.of("nickname", "닉네임");
        Map<String, Object> kakaoAccount = Map.of("profile", profile); // email 없음
        Map<String, Object> body = Map.of(
                "id", 999L,
                "kakao_account", kakaoAccount);

        OAuthUserInfo info = client.mapToUserInfo(body);

        assertThat(info.providerUserId()).isEqualTo("999");
        assertThat(info.email()).isNull();
        assertThat(info.name()).isEqualTo("닉네임");
        assertThat(info.profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("kakao_account 자체가 없어도 id 만으로 안전하게 매핑한다")
    void mapToUserInfo_withoutKakaoAccount_mapsIdOnly() {
        Map<String, Object> body = new HashMap<>();
        body.put("id", 111L);

        OAuthUserInfo info = client.mapToUserInfo(body);

        assertThat(info.provider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(info.providerUserId()).isEqualTo("111");
        assertThat(info.email()).isNull();
        assertThat(info.name()).isNull();
        assertThat(info.profileImageUrl()).isNull();
    }
}
