package com.wevo.backend.project.service;

import com.wevo.backend.project.domain.OutputType;

/**
 * AI 입력 조립에 허용된 프로젝트 최소 정보.
 *
 * <p>선택 필드의 blank는 {@code null}로 정규화한다. 원문 길이 절단은 이 경계에서 하지 않으며,
 * 기능별 budget과 함께 처리해야 하므로 AI-05 assembler가 한 번만 담당한다.
 */
public record ProjectAiContext(
        Long projectId,
        String title,
        String description,
        String ideaText,
        String audience,
        OutputType outputType
) {
}
