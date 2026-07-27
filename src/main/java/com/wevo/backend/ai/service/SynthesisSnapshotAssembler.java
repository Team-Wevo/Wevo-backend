package com.wevo.backend.ai.service;

import com.wevo.backend.issue.service.GapAnswerInputView;
import com.wevo.backend.issue.service.SynthesisSetQueryService;
import com.wevo.backend.opinion.service.OpinionSynthesisQueryService;
import com.wevo.backend.opinion.service.SubmittedOpinionView;
import com.wevo.backend.section.service.SectionSynthesisStateService;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 섹션의 현재 상태에서 AI 의견 정리 입력 스냅샷을 조립한다. (§3.8.1)
 *
 * <p>요청 시점(멱등키 계산)과 실행 시작·완료 시점(스냅샷 재대조)이 <b>같은 조립 경로</b>를 쓰도록
 * 한 곳에 모았다 — 조립 규칙이 갈라지면 오래된 결과 폐기 판정이 어긋난다. 각 도메인 데이터는
 * 해당 도메인의 읽기 경계(§6)를 통해서만 가져온다.
 */
@Component
public class SynthesisSnapshotAssembler {

    private final OpinionSynthesisQueryService opinionSynthesisQueryService;
    private final SynthesisSetQueryService synthesisSetQueryService;
    private final SectionSynthesisStateService sectionSynthesisStateService;

    public SynthesisSnapshotAssembler(OpinionSynthesisQueryService opinionSynthesisQueryService,
                                      SynthesisSetQueryService synthesisSetQueryService,
                                      SectionSynthesisStateService sectionSynthesisStateService) {
        this.opinionSynthesisQueryService = opinionSynthesisQueryService;
        this.synthesisSetQueryService = synthesisSetQueryService;
        this.sectionSynthesisStateService = sectionSynthesisStateService;
    }

    @Transactional(readOnly = true)
    public SynthesisInputSnapshot assemble(Long projectSectionId) {
        List<SubmittedOpinionView> opinions =
                opinionSynthesisQueryService.findSubmittedOpinions(projectSectionId);
        List<GapAnswerInputView> gapAnswers =
                synthesisSetQueryService.findCurrentGapAnswerInput(projectSectionId);
        long gateGeneration =
                sectionSynthesisStateService.currentOpinionGateGeneration(projectSectionId);
        return new SynthesisInputSnapshot(opinions, gapAnswers, gateGeneration);
    }
}
