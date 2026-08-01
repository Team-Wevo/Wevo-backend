package com.wevo.backend.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.ai.domain.ProjectFlowFindingType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectFlowReviewResponse(
        boolean exists,
        Boolean outdated,
        LatestJobResponse latestJob,
        ResultResponse lastSuccessfulResult
) {
    public static ProjectFlowReviewResponse notExecuted() {
        return new ProjectFlowReviewResponse(false, null, null, null);
    }
    public record LatestJobResponse(UUID requestId, AiRequestStatus status, FailureResponse failure) { }
    public record FailureResponse(String errorCode, String message) { }
    public record ResultResponse(UUID requestId, boolean current, LocalDateTime checkedAt,
                                 int checkedSectionCount, int findingCount,
                                 List<CheckedSectionResponse> checkedSections,
                                 List<FindingResponse> findings) { }
    public record CheckedSectionResponse(Long sectionId, String sectionKey, String title,
                                         int confirmedVersion) { }
    public record FindingResponse(int order, ProjectFlowFindingType type,
                                  List<SectionExcerptResponse> sections,
                                  String description, String suggestion) { }
    public record SectionExcerptResponse(Long sectionId, int confirmedVersion, String targetExcerpt) { }
}
