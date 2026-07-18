package com.wevo.backend.section.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 섹션 초안(본문) 저장 요청.
 *
 * @param content     저장할 본문. 작성 중 비어 있을 수 있어 blank 는 허용하되 null 은 허용하지 않는다.
 * @param baseVersion 클라이언트가 편집을 시작한 기준 본문 버전(= 섹션 조회의 {@code contentVersion}).
 *                    초안이 아직 없으면 {@code 0}. 서버의 최신 버전과 다르면 409 로 거부한다(낙관적 충돌).
 */
public record SectionDraftSaveRequest(
        @NotNull String content,
        @NotNull @PositiveOrZero Integer baseVersion
) {
}
