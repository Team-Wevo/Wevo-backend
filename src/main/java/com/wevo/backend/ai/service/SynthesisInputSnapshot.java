package com.wevo.backend.ai.service;

import com.wevo.backend.issue.service.GapAnswerInputView;
import com.wevo.backend.opinion.service.SubmittedOpinionView;
import java.util.List;

/**
 * AI 의견 정리의 입력 스냅샷. (§3.8.1 입력 스냅샷 바인딩)
 *
 * <p>동일 입력 판정과 <b>오래된 결과 폐기</b>의 기준이 되는 값 묶음이다 — 마감 시점 제출 의견,
 * 기존 GAP 보충 답변(직접 + 승계 합집합), 마감 세대({@code opinionGateGeneration})로 구성한다.
 * 의견이 그대로여도 재오픈으로 세대가 바뀌면 다른 스냅샷으로 취급한다.
 *
 * @param opinions              제출된 의견 (제출본)
 * @param gapAnswers            현재 세트 기준 GAP 답변 합집합 (answerId 중복 제거)
 * @param opinionGateGeneration 의견 수집 마감 세대
 */
public record SynthesisInputSnapshot(
        List<SubmittedOpinionView> opinions,
        List<GapAnswerInputView> gapAnswers,
        long opinionGateGeneration
) {

    /** 제출 의견이 하나도 없으면 정리를 실행할 수 없다. */
    public boolean hasNoOpinion() {
        return opinions.isEmpty();
    }
}
