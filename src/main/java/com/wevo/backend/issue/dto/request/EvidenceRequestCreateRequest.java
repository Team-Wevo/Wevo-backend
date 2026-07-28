package com.wevo.backend.issue.dto.request;

import jakarta.validation.constraints.NotNull;

/** GAP 쟁점의 관련 의견 작성자에게 보내는 추가 근거 요청. (API_SPEC §3.9.2) */
public record EvidenceRequestCreateRequest(
        @NotNull
        Long targetUserId
) {
}
