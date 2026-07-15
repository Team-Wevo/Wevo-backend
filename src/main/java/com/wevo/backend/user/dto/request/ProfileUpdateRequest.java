package com.wevo.backend.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 내 프로필 수정 요청. (제품 정책서 §1.1 — 표시 이름만 수정 가능)
 *
 * @param name 표시 이름 (1~100자)
 */
public record ProfileUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String name
) {

    /**
     * 표시 이름 앞뒤 공백을 제거한다. 검증(@NotBlank·@Size)은 정리된 값을 기준으로 수행된다.
     * ({@code strip()} 은 유니코드 공백까지 처리 — 전각 공백 등)
     */
    public ProfileUpdateRequest {
        if (name != null) {
            name = name.strip();
        }
    }
}
