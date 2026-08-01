package com.wevo.backend.review.dto.request;

import com.wevo.backend.review.domain.ReviewLinkStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 외부 검토 링크 상태 변경 요청. (팀장 전용 — 수동 종료)
 *
 * <p>{@code CLOSED} 만 허용한다. {@code OUTDATED}(본문 수정으로 인한 만료)와
 * {@code ACTIVE}(발급) 는 서버가 정하는 상태라 요청으로 지정할 수 없다.
 *
 * @param status 변경할 상태 (CLOSED 만 허용 — 그 외는 400 C001)
 */
public record ReviewLinkStatusUpdateRequest(
        @NotNull ReviewLinkStatus status
) {
}
