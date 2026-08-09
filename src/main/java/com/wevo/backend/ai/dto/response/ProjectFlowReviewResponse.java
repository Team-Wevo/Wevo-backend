package com.wevo.backend.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.domain.ProjectFlowFindingType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"exists", "outdated"})
public record ProjectFlowReviewResponse(
        boolean exists,
        Boolean outdated,
        LatestJobResponse latestJob,
        ResultResponse lastSuccessfulResult
) {
    public static ProjectFlowReviewResponse notExecuted() {
        return new ProjectFlowReviewResponse(false, null, null, null);
    }
    @Schema(requiredProperties = {"requestId", "status"})
    public record LatestJobResponse(UUID requestId, AiRequestStatus status, FailureResponse failure) { }
    @Schema(requiredProperties = {"errorCode", "message"})
    public record FailureResponse(String errorCode, String message) { }
    @Schema(requiredProperties = {
            "requestId", "current", "checkedAt", "checkedSectionCount", "findingCount", "checkedSections",
            "findings"
    })
    public record ResultResponse(UUID requestId, boolean current, LocalDateTime checkedAt,
                                 int checkedSectionCount, int findingCount,
                                 List<CheckedSectionResponse> checkedSections,
                                 List<FindingResponse> findings) { }
    @Schema(requiredProperties = {"sectionId", "sectionKey", "title", "confirmedVersion"})
    public record CheckedSectionResponse(Long sectionId, String sectionKey, String title,
                                         int confirmedVersion) { }
    @Schema(
            name = "ProjectFlowFindingResponse",
            requiredProperties = {"order", "type", "sections", "description", "suggestion"}
    )
    public record FindingResponse(int order, ProjectFlowFindingType type,
                                  List<SectionExcerptResponse> sections,
                                  String description, String suggestion) { }
    @Schema(requiredProperties = {"sectionId", "confirmedVersion", "targetExcerpt"})
    public record SectionExcerptResponse(Long sectionId, int confirmedVersion, String targetExcerpt) { }
}
