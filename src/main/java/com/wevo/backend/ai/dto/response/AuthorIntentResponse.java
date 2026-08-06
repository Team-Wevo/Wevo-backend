package com.wevo.backend.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.section.domain.SectionAuthorIntentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"contentVersion", "status"})
public record AuthorIntentResponse(
        Integer contentVersion,
        SectionAuthorIntentStatus status,
        String aiSuggestedIntent,
        String confirmedIntent,
        Long confirmedByUserId,
        LocalDateTime confirmedAt,
        LatestJobResponse latestJob
) {

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
}
