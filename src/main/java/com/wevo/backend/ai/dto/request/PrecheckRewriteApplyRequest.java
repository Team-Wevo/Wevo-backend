package com.wevo.backend.ai.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/** 화면에 표시한 사전 검토 수정안을 해당 본문 버전에 적용하는 요청. */
public record PrecheckRewriteApplyRequest(
        @NotNull UUID requestId,
        @NotNull @Positive Integer checkedContentVersion
) {
}
