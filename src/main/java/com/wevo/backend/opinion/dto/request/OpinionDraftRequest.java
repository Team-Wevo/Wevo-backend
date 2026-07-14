package com.wevo.backend.opinion.dto.request;

import com.wevo.backend.opinion.domain.Opinion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 내 의견 임시저장 요청. (제품 정책서 §5 — 의견은 20~1,000자, 앱 레벨 검증)
 */
public record OpinionDraftRequest(
        @NotBlank
        @Size(min = Opinion.MIN_CONTENT_LENGTH, max = Opinion.MAX_CONTENT_LENGTH)
        String content
) {
}
