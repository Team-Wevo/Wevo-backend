package com.wevo.backend.ai.service;

import com.wevo.backend.ai.dto.model.IssueDetectionIssueOutput;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI-07 result writer가 소비할 검증 완료 쟁점 후보와 입력 coverage.
 *
 * <p>이 타입에는 사용자 식별자나 해결 상태가 없으며 모든 후보는 새 set에서 PENDING으로 저장한다.</p>
 */
public record IssueDetectionResult(
        List<IssueDetectionIssueOutput> issues,
        List<Long> eligibleOpinionIds,
        List<Long> coveredOpinionIds
) {

    public IssueDetectionResult {
        if (issues == null || eligibleOpinionIds == null || coveredOpinionIds == null) {
            throw new IllegalArgumentException("쟁점 결과와 opinion coverage는 필수입니다.");
        }
        issues = List.copyOf(issues);
        eligibleOpinionIds = List.copyOf(eligibleOpinionIds);
        coveredOpinionIds = List.copyOf(coveredOpinionIds);
        Set<Long> eligible = new HashSet<>(eligibleOpinionIds);
        Set<Long> covered = new HashSet<>(coveredOpinionIds);
        if (eligible.size() != eligibleOpinionIds.size()
                || covered.size() != coveredOpinionIds.size()
                || !eligible.equals(covered)) {
            throw new IllegalArgumentException("쟁점 감지 결과는 전체 eligible opinion을 포함해야 합니다.");
        }
    }
}
