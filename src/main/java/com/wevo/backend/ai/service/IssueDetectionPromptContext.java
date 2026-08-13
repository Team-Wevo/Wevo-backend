package com.wevo.backend.ai.service;

import com.wevo.backend.ai.context.AiOpinionContext;
import com.wevo.backend.ai.context.AiProjectBrief;
import com.wevo.backend.ai.context.AiProjectIdentity;
import com.wevo.backend.ai.context.AiSectionContext;
import com.wevo.backend.ai.dto.model.IssueDetectionOutput;
import java.util.List;

/** 직접 분석, chunk 후보 추출과 최종 병합이 같은 prompt/schema를 사용하는 입력 모델. */
public record IssueDetectionPromptContext(
        String mode,
        AiProjectIdentity project,
        AiProjectBrief projectBrief,
        AiSectionContext section,
        List<AiOpinionContext> opinions,
        List<PartialIssueDetection> partials
) {

    public static final String DIRECT = "DIRECT";
    public static final String PARTIAL = "PARTIAL";
    public static final String FINAL_MERGE = "FINAL_MERGE";

    public IssueDetectionPromptContext {
        if (!List.of(DIRECT, PARTIAL, FINAL_MERGE).contains(mode)) {
            throw new IllegalArgumentException("쟁점 감지 mode가 유효하지 않습니다.");
        }
        opinions = List.copyOf(opinions);
        partials = List.copyOf(partials);
        if (FINAL_MERGE.equals(mode)) {
            if (!opinions.isEmpty() || partials.isEmpty()) {
                throw new IllegalArgumentException("FINAL_MERGE는 partial 결과만 포함해야 합니다.");
            }
            for (int index = 0; index < partials.size(); index++) {
                if (partials.get(index).chunkIndex() != index + 1) {
                    throw new IllegalArgumentException("쟁점 감지 partial index는 1부터 연속되어야 합니다.");
                }
            }
        } else if (!partials.isEmpty()) {
            throw new IllegalArgumentException("직접·부분 쟁점 감지는 opinion만 포함해야 합니다.");
        }
    }

    public record PartialIssueDetection(
            int chunkIndex,
            List<Long> coveredOpinionIds,
            IssueDetectionOutput output
    ) {
        public PartialIssueDetection {
            if (chunkIndex <= 0 || output == null) {
                throw new IllegalArgumentException("쟁점 감지 partial이 유효하지 않습니다.");
            }
            coveredOpinionIds = List.copyOf(coveredOpinionIds);
        }
    }
}
