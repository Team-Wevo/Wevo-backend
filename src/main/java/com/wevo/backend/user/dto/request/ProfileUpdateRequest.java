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
}
