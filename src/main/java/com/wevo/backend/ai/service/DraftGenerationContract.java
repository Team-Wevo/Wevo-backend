package com.wevo.backend.ai.service;

/** AI 초안 생성의 입력·prompt·schema 버전 계약. */
public final class DraftGenerationContract {

    public static final String PROMPT_VERSION = "draft-generation:v1";
    public static final String SCHEMA_VERSION = "draft-generation-output:v1";

    private DraftGenerationContract() {
    }
}
