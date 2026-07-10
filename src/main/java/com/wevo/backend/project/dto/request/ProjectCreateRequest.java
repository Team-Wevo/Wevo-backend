package com.wevo.backend.project.dto.request;

import com.wevo.backend.project.domain.OutputType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 프로젝트 생성 요청. (제품 정책서 §2.2)
 *
 * @param title       프로젝트 이름 (선택)
 * @param ideaText    아이디어 자유 입력 (필수)
 * @param resultType  결과물 유형 — 유형에 따라 고정 6섹션이 생성된다 (필수)
 * @param audience    전달 대상 (선택, 심사위원/교수/팀원 등 자유 입력)
 */
public record ProjectCreateRequest(
        @Size(max = 200) String title,
        @NotBlank String ideaText,
        @NotNull OutputType resultType,
        @Size(max = 200) String audience
) {
}
