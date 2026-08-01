package com.wevo.backend.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.section.domain.SectionAuthorIntentStatus;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
    public record LatestJobResponse(
            UUID requestId,
            AiRequestStatus status,
            FailureResponse failure
    ) {
    }

    public record FailureResponse(String errorCode, String message) {
    }
}
