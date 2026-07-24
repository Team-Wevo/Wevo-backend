package com.wevo.backend.ai.context;

/** 프로젝트 배경이 필요한 기능에서만 포함하는 선택 정보. */
public record AiProjectBrief(
        String description,
        String ideaText,
        String audience
) {
}
