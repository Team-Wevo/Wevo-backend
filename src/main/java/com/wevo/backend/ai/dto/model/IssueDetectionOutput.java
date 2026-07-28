package com.wevo.backend.ai.dto.model;

import java.util.List;

/** 충돌·정보 공백 감지의 구조화 출력. 빈 목록은 쟁점이 없는 정상 결과다. */
public record IssueDetectionOutput(List<IssueDetectionIssueOutput> issues) {

    public IssueDetectionOutput {
        issues = issues == null ? null : List.copyOf(issues);
    }
}
