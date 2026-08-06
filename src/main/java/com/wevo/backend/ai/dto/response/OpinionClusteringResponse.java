package com.wevo.backend.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.ai.domain.AiRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 최신 실행과 현재 snapshot에 유효한 마지막 성공 set을 분리한 조회 응답. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"exists", "canViewResult", "stale"})
public record OpinionClusteringResponse(
        boolean exists,
        Boolean canViewResult,
        Boolean stale,
        LatestJobResponse latestJob,
        CurrentSetResponse currentSet
) {
    public static OpinionClusteringResponse notExecuted(boolean canViewResult) {
        return new OpinionClusteringResponse(false, canViewResult, null, null, null);
    }

    public static OpinionClusteringResponse of(
            boolean canViewResult,
            boolean stale,
            LatestJobResponse latestJob,
            CurrentSetResponse currentSet
    ) {
        return new OpinionClusteringResponse(true, canViewResult, stale, latestJob, currentSet);
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

    public record CurrentSetResponse(
            UUID setId,
            long sourceGateGeneration,
            int totalOpinionCount,
            int coveredCount,
            LocalDateTime createdAt,
            List<ClusterResponse> clusters
    ) {
    }

    @Schema(requiredProperties = {"order", "title", "summary", "opinionIds"})

    public record ClusterResponse(
            int order,
            String title,
            String summary,
            List<Long> opinionIds
    ) {
    }
}
