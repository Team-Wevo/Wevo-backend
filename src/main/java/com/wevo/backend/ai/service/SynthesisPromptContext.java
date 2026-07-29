package com.wevo.backend.ai.service;

import com.wevo.backend.ai.context.AiGapAnswerContext;
import com.wevo.backend.ai.context.AiOpinionContext;
import java.util.List;

/** 부분 합성과 최종 병합이 같은 prompt/schema를 사용할 수 있게 하는 provider 입력 모델. */
public record SynthesisPromptContext(
        String mode,
        List<AiOpinionContext> opinions,
        List<AiGapAnswerContext> gapAnswers,
        List<PartialSynthesis> partials
) {

    public static final String PARTIAL = "PARTIAL";
    public static final String FINAL_MERGE = "FINAL_MERGE";

    public SynthesisPromptContext {
        opinions = List.copyOf(opinions);
        gapAnswers = List.copyOf(gapAnswers);
        partials = List.copyOf(partials);
    }

    public record PartialSynthesis(
            int chunkIndex,
            List<Long> coveredOpinionIds,
            SynthesisAiOutput output
    ) {
        public PartialSynthesis {
            coveredOpinionIds = List.copyOf(coveredOpinionIds);
        }
    }
}
