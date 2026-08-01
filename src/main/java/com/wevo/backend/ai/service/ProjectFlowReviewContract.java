package com.wevo.backend.ai.service;

/** 프로젝트 전체 흐름 점검의 입력·prompt·schema·출력 제한 계약. */
public final class ProjectFlowReviewContract {
    public static final String SOURCE_VERSION = "project-flow-review-source:v1";
    public static final String PROMPT_VERSION = "project-flow-review:v1";
    public static final String SCHEMA_VERSION = "project-flow-review-output:v1";
    public static final int MAX_FINDING_COUNT = 50;
    public static final int MAX_EXCERPT_LENGTH = 500;
    public static final int MAX_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_SUGGESTION_LENGTH = 1000;

    private ProjectFlowReviewContract() {
    }
}
