package com.wevo.backend.section.service;

/** 직접 dependency 조회 결과. JPA 엔티티를 도메인 밖으로 노출하지 않는다. */
public record SectionDependencyReference(
        Long sectionId,
        String templateKey,
        int sectionOrder
) {
}
