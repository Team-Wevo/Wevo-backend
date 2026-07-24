package com.wevo.backend.section.service;

/** 특정 section draft version과 본문. */
public record SectionVersionedContent(
        Long sectionId,
        int contentVersion,
        String content
) {
}
