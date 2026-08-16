package com.wevo.backend.ai.service;

/** 프로젝트 제목 자동 생성 기능의 prompt·schema 버전과 제목 길이 정책. */
public final class ProjectTitleContract {

    public static final String PROMPT_VERSION = "project-title:v1";
    public static final String SCHEMA_VERSION = "project-title-output:v1";

    /** 저장 컬럼 {@code projects.title} 상한과 맞춘다. (ProjectCreateRequest @Size(max=200)) */
    public static final int MAX_TITLE_LENGTH = 200;

    private ProjectTitleContract() {
    }

    public static boolean isValidTitle(String title) {
        if (title == null) {
            return false;
        }
        String trimmed = title.strip();
        return !trimmed.isBlank()
                && trimmed.length() <= MAX_TITLE_LENGTH
                && trimmed.indexOf('\n') < 0
                && trimmed.indexOf('\r') < 0;
    }
}
