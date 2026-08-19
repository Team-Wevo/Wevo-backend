package com.wevo.backend.ai.service;

import java.util.Set;

/** 의견 내용 가드레일의 prompt·schema 버전과 판정 사유 코드. */
public final class OpinionGuardrailContract {

    public static final String PROMPT_VERSION = "opinion-guardrail:v1";
    public static final String SCHEMA_VERSION = "opinion-guardrail-output:v1";

    /** 통과(문제 없음). */
    public static final String REASON_OK = "OK";
    /** 알아들을 수 없는 글(횡설수설·난타 등). */
    public static final String REASON_GIBBERISH = "GIBBERISH";
    /** 섹션 질문과 무관한 딴 주제. */
    public static final String REASON_OFF_TOPIC = "OFF_TOPIC";

    public static final Set<String> REASONS =
            Set.of(REASON_OK, REASON_GIBBERISH, REASON_OFF_TOPIC);
    /** 거부 사유(통과가 아닌 값). */
    public static final Set<String> REJECT_REASONS = Set.of(REASON_GIBBERISH, REASON_OFF_TOPIC);

    private OpinionGuardrailContract() {
    }
}
