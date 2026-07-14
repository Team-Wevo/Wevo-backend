package com.wevo.backend.review.dto.request;

import com.wevo.backend.review.domain.ReviewLinkStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 외부 검토 링크 상태 변경 요청. (팀장 전용)
 *
 * <p> {@code OUTDATED}(본문 수정으로 인한 만료)는 직접 지정하지 않는다.
 *
 * @param status 변경할 상태 (CLOSED 만 허용)
 */
public record ReviewLinkStatusUpdateRequest(
        @NotNull ReviewLinkStatus status
) {
}
