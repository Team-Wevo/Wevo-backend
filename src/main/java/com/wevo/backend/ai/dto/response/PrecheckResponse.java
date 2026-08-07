package com.wevo.backend.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.domain.AiSectionFindingType;
import com.wevo.backend.section.domain.AiCheckStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** 최신 precheck 실행과 마지막 성공 결과를 분리한 조회 모델. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"exists"})
public record PrecheckResponse(
        boolean exists,
        AiCheckStatus aiCheckStatus,
        LatestJobResponse latestJob,
        CurrentResultResponse currentResult
) {

    public static PrecheckResponse notExecuted() {
        return new PrecheckResponse(false, null, null, null);
    }

    public static PrecheckResponse of(
            AiCheckStatus aiCheckStatus,
            LatestJobResponse latestJob,
            CurrentResultResponse currentResult
    ) {
        return new PrecheckResponse(true, aiCheckStatus, latestJob, currentResult);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(requiredProperties = {"requestId", "status"})
    public record LatestJobResponse(
            UUID requestId,
            AiRequestStatus status,
            FailureResponse failure
    ) {
    }

    @Schema(requiredProperties = {"errorCode", "message"})
    public record FailureResponse(String errorCode, String message) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(requiredProperties = {"resultId", "checkedContentVersion", "findings", "rewriteApplied"})
    public record CurrentResultResponse(
            UUID resultId,
            Integer checkedContentVersion,
            List<FindingResponse> findings,
            RewriteResponse rewrite,
            boolean rewriteApplied,
            Integer appliedContentVersion
    ) {
    }

    @Schema(
            name = "PrecheckFindingResponse",
            requiredProperties = {"type", "targetExcerpt", "comment", "suggestion"}
    )
    public record FindingResponse(
            AiSectionFindingType type,
            String targetExcerpt,
            String comment,
            String suggestion
    ) {
    }

    @Schema(requiredProperties = {"content", "changedCount"})

    public record RewriteResponse(String content, Integer changedCount) {
    }
}
