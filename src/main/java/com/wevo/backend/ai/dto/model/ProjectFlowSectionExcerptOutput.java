package com.wevo.backend.ai.dto.model;

/** finding이 가리키는 확정 섹션과 검증 가능한 원문 일부. */
public record ProjectFlowSectionExcerptOutput(Long sectionId, String targetExcerpt) {
}
