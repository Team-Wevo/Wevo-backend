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
    // required 를 지정하지 않는다 — 같은 이름의 중첩 레코드가 PrecheckResponse 에도 있고 필드 구성이
    // 서로 다르다. Springdoc 은 단순 이름으로 스키마를 만들어 둘이 하나로 합쳐지므로, 한쪽 기준으로
    // required 를 걸면 다른 쪽 응답과 어긋난다. 이름을 분리한 뒤에 지정한다. (#184)
    public record FindingResponse(int order, ProjectFlowFindingType type,
                                  List<SectionExcerptResponse> sections,
                                  String description, String suggestion) { }
    @Schema(requiredProperties = {"sectionId", "confirmedVersion", "targetExcerpt"})
    public record SectionExcerptResponse(Long sectionId, int confirmedVersion, String targetExcerpt) { }
}
