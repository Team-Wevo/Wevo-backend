package com.wevo.backend.section.service;

/** 직접 상위 section에서 실제로 사용한 version과 본문. */
public record PrerequisiteSectionContent(
        Long sectionId,
        String templateKey,
        int sectionOrder,
        int contentVersion,
        String content
) {
}
