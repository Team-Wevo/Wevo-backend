package com.wevo.backend.ai.context;

import com.wevo.backend.ai.domain.AiFeature;
import java.util.List;

/**
 * current synthesis와 직접 상위 확정본을 사용하는 초안 생성 입력.
 *
 * <p>AI-06의 상세 쟁점 결정 계약은 해당 기능 구현 시 이 모델에 별도 필드로 연결한다.</p>
 */
public record DraftGenerationContext(
        AiProjectIdentity project,
        AiProjectBrief projectBrief,
        AiSectionContext section,
        AiDraftSynthesisContext synthesis,
        AiBaseDraftContext baseDraft,
        List<AiPrerequisiteContext> prerequisites
) implements AiFeatureContext {

    public DraftGenerationContext {
        prerequisites = List.copyOf(prerequisites);
    }

    @Override
    public AiFeature feature() {
        return AiFeature.DRAFT_GENERATION;
    }

    @Override
    public String sourceVersion() {
        return "draft-v1-s" + synthesis.synthesisSetId()
                + "-g" + synthesis.opinionGateGeneration();
    }
}
