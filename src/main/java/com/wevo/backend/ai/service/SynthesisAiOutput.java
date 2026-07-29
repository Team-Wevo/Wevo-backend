package com.wevo.backend.ai.service;

import com.wevo.backend.ai.dto.model.IssueDetectionIssueOutput;
import java.util.List;

/**
 * AI 의견 정리의 구조화 출력. (§5.1 — 합의점 + 쟁점 + 전체 입력 coverage)
 *
 * <p>쟁점 출력은 AI-06의 단일 계약인 {@link IssueDetectionIssueOutput}을 재사용한다. 합의점
 * evidence와 coverage는 서로 다른 의미다. evidence는 합의점을 직접 뒷받침하는 최소 의견 집합이고,
 * coverage는 해당 단계가 실제로 검토한 전체 입력 의견 집합이다.
 *
 * @param consensusSummary            합의점 요약 (§5.1.1)
 * @param consensusEvidenceOpinionIds 합의점을 직접 뒷받침하는 제출 의견 ID
 * @param coveredOpinionIds           이 결과가 검토한 전체 제출 의견 ID
 * @param issues                      쟁점 목록 (표시 순서 = 목록 순서)
 */
public record SynthesisAiOutput(
        String consensusSummary,
        List<Long> consensusEvidenceOpinionIds,
        List<Long> coveredOpinionIds,
        List<IssueDetectionIssueOutput> issues
) {

    public SynthesisAiOutput {
        consensusEvidenceOpinionIds = consensusEvidenceOpinionIds == null
                ? null : List.copyOf(consensusEvidenceOpinionIds);
        coveredOpinionIds = coveredOpinionIds == null ? null : List.copyOf(coveredOpinionIds);
        issues = issues == null ? null : List.copyOf(issues);
    }
}
