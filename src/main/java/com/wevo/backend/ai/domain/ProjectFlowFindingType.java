package com.wevo.backend.ai.domain;

/** 외부 API에도 그대로 노출되는 프로젝트 전체 흐름 finding 유형. */
public enum ProjectFlowFindingType {
    CLAIM_OR_NUMBER_CONTRADICTION,
    LOGICAL_CONNECTION_MISSING,
    REDUNDANT_CONTENT,
    TERMINOLOGY_AUDIENCE_TONE_MISMATCH,
    DEPENDENCY_NOT_REFLECTED
}
