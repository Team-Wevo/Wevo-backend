package com.wevo.backend.ai.service;

/** AI 초안 생성의 입력·prompt·schema 버전 계약. */
public final class DraftGenerationContract {

    public static final String PROMPT_VERSION = "draft-generation:v2";
    public static final String SCHEMA_VERSION =
            DraftGenerationOutputDefinition.SCHEMA_ID.trackingValue();

    private DraftGenerationContract() {
    }

    public static String unresolvedGapMarker(Long issueId) {
        if (issueId == null || issueId <= 0) {
            throw new IllegalArgumentException("미답변 GAP issue ID는 양수여야 합니다.");
        }
        return "[미확인:GAP-" + issueId + "]";
    }
}
