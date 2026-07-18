package com.wevo.backend.project.dto.request;

import com.wevo.backend.project.domain.OutputType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 프로젝트 생성 요청. (제품 정책서 §2.2 — API_SPEC §3.2.1)
 *
 * @param title       프로젝트 이름 (선택 — 생략·공백이면 서버 기본값 저장)
 * @param ideaText    아이디어 자유 입력 (필수, 최대 2,000자 — AI 입력으로 사용되므로 상한 고정)
 * @param resultType  결과물 유형 — 유형에 따라 고정 6섹션이 생성된다 (필수)
 * @param audience    전달 대상 (필수 — §2.2 생성 흐름 3단계 "대상 선택". 심사위원/교수/팀원 등 자유 입력)
 */
public record ProjectCreateRequest(
        @Size(max = 200) String title,
        @NotBlank @Size(max = 2000) String ideaText,
        @NotNull OutputType resultType,
        @NotBlank @Size(max = 200) String audience
) {
}
