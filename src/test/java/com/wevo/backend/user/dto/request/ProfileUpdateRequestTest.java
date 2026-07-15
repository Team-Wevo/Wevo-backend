package com.wevo.backend.user.dto.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileUpdateRequestTest {

    @Test
    @DisplayName("이름 앞뒤 공백은 생성 시 제거된다")
    void trimsSurroundingWhitespace() {
        ProfileUpdateRequest request = new ProfileUpdateRequest("  가나디  ");

        assertThat(request.name()).isEqualTo("가나디");
    }

    @Test
    @DisplayName("전각 공백 등 유니코드 공백도 제거된다")
    void stripsUnicodeWhitespace() {
        ProfileUpdateRequest request = new ProfileUpdateRequest("　호석　");

        assertThat(request.name()).isEqualTo("호석");
    }

    @Test
    @DisplayName("name이 null이면 그대로 null을 유지한다")
    void keepsNullAsIs() {
        ProfileUpdateRequest request = new ProfileUpdateRequest(null);

        assertThat(request.name()).isNull();
    }
}
