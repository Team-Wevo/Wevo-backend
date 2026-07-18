package com.wevo.backend.section.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 섹션 초안(본문) 저장 요청. (API_SPEC §3.7.2)
 *
 * @param content     저장할 본문. <b>공백 불가·최대 {@value #MAX_CONTENT_LENGTH}자</b> 는 초안 도메인
 *                    불변식이라 수동 저장뿐 아니라 AI 초안 생성·수정안 적용 등 모든 저장 경로에
 *                    동일하게 적용한다. 위반 시 {@code 400 C001}.
 * @param baseVersion 클라이언트가 편집을 시작한 기준 본문 버전(= 섹션 조회의 {@code contentVersion}).
 *                    초안이 아직 없으면 {@code 0}. 서버의 최신 버전과 다르면 409 로 거부한다(낙관적 충돌).
 */
public record SectionDraftSaveRequest(
        @NotBlank @Size(max = SectionDraftSaveRequest.MAX_CONTENT_LENGTH) String content,
        @NotNull @PositiveOrZero Integer baseVersion
) {

    public static final int MAX_CONTENT_LENGTH = 10_000;
}
