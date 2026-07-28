package com.wevo.backend.ai.domain;

/** 제품 정책 §5.3.1의 AI 사전 검토 finding 유형. */
public enum AiSectionFindingType {
    UNCLEAR_SENTENCE,
    HIDDEN_ASSUMPTION,
    PREREQUISITE_CONFLICT,
    READER_QUESTION
}
