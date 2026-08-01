package com.wevo.backend.ai.service;

public final class ReviewIntentComparisonContract {

    public static final String PROMPT_VERSION = "review-intent-comparison:v1";
    public static final String SCHEMA_VERSION = "review-intent-comparison-output:v1";

    private static final java.util.regex.Pattern SOURCE_VERSION =
            java.util.regex.Pattern.compile("review-submission-(\\d+)-draft-v\\d+");

    public static long submissionId(String sourceVersion) {
        java.util.regex.Matcher matcher = SOURCE_VERSION.matcher(sourceVersion == null ? "" : sourceVersion);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("검토 비교 sourceVersion이 유효하지 않습니다.");
        }
        return Long.parseLong(matcher.group(1));
    }

    private ReviewIntentComparisonContract() {
    }
}
