package com.wevo.backend.ai.context;

import com.wevo.backend.ai.domain.AiFeature;

/**
 * 프로젝트 제목 자동 생성 입력 — 생성 시 입력한 아이디어·결과물 유형·전달 대상만 포함한다.
 * (개인 식별정보는 담지 않는다 — CLAUDE.md §7)
 */
public record ProjectTitleContext(
        Long projectId,
        String ideaText,
        String resultType,
        String audience
) implements AiFeatureContext {

    @Override
    public AiFeature feature() {
        return AiFeature.PROJECT_TITLE_SUGGESTION;
    }

    @Override
    public String sourceVersion() {
        // 제목 생성 입력은 생성 시점에 고정된다 — 프로젝트 단위 단일 버전.
        return "project-v1";
    }
}
