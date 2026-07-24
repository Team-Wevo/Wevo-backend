package com.wevo.backend.ai.context;

import com.wevo.backend.project.domain.OutputType;

/** 모든 섹션 AI 기능에 필요한 프로젝트 식별 정보. */
public record AiProjectIdentity(
        Long projectId,
        String title,
        OutputType outputType
) {
}
