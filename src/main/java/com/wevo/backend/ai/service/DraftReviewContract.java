package com.wevo.backend.ai.service;

/** AI 사전 검토의 prompt/schema 버전 계약. */
public final class DraftReviewContract {

    public static final String PROMPT_VERSION = "draft-review:v1";
    public static final String SCHEMA_VERSION = "draft-review-output:v1";

    private DraftReviewContract() {
    }
}
