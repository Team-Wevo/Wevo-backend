package com.wevo.backend.ai.domain;

/** 외부 AI 작업 조회 API에 노출하는 기능 이름. 내부 실행 단위는 직접 노출하지 않는다. */
public enum AiRequestFeature {
    SYNTHESIS,
    DRAFT_GENERATION,
    PRECHECK;

    public static AiRequestFeature from(AiFeature feature) {
        return switch (feature) {
            case ISSUE_DETECTION, OPINION_SYNTHESIS -> SYNTHESIS;
            case DRAFT_GENERATION -> DRAFT_GENERATION;
            case DRAFT_REVIEW -> PRECHECK;
        };
    }
}
