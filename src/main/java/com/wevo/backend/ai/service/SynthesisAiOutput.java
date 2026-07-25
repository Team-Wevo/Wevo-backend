package com.wevo.backend.ai.service;

import com.wevo.backend.issue.domain.IssueType;
import java.util.List;

/**
 * AI 의견 정리의 구조화 출력. (§5.1 — 합의점 + 쟁점)
 *
 * <p>{@code StructuredOutputDefinition}이 JSON 스키마를 생성하므로 반드시 record이며 모든 필드는
 * 항상 존재한다(GAP 쟁점은 {@code question}이 빈 문자열, {@code options}가 빈 배열). 값의 의미
 * 검증(개수 상한·CONFLICT/GAP 규칙·의견 참조 유효성)은 {@link SynthesisOutputValidator}가 담당한다.
 *
 * @param consensusSummary 합의점 요약 (§5.1.1)
 * @param issues           쟁점 목록 (표시 순서 = 목록 순서)
 */
public record SynthesisAiOutput(
        String consensusSummary,
        List<IssueOut> issues
) {

    /**
     * @param type              CONFLICT / GAP
     * @param description       충돌 설명 / 부족한 정보 (§5.1.2·§5.1.4)
     * @param question          팀장이 결정할 질문 — CONFLICT만 (GAP은 빈 문자열)
     * @param options           선택지 — CONFLICT만 (GAP은 빈 배열, §5.1.3)
     * @param relatedOpinionIds 근거가 된 제출 의견 ID 목록
     */
    public record IssueOut(
            IssueType type,
            String description,
            String question,
            List<String> options,
            List<Long> relatedOpinionIds
    ) {
    }
}
