package com.wevo.backend.review.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 팀장의 수정 요청 해소 처리 요청. (§6.1.1 — 수정 안 하고 합의된 경우)
 *
 * @param resolved 해소 여부 (true=해소 처리, false=해소 취소)
 */
public record TeamReviewResolveRequest(
        @NotNull Boolean resolved
) {
}
