package com.wevo.backend.ai.service;

/** 의견 자동 분류의 입력·prompt·schema 및 제품 제한 계약. */
public final class OpinionClusteringContract {

    public static final String SOURCE_VERSION = "opinion-clustering-source:v1";
    public static final String PROMPT_VERSION = "opinion-clustering:v1";
    public static final String SCHEMA_VERSION = "opinion-clustering-output:v1";
    public static final int MIN_OPINION_COUNT = 3;
    public static final int MAX_CLUSTER_COUNT = 4;
    public static final int MAX_TITLE_LENGTH = 60;
    public static final int MAX_SUMMARY_LENGTH = 500;

    private OpinionClusteringContract() {
    }
}
