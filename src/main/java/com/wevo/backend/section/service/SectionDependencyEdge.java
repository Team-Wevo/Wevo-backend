package com.wevo.backend.section.service;

/** 결과물 유형 안의 직접 REQUIRES 관계. from이 to에 의존한다. */
public record SectionDependencyEdge(String fromTemplateKey, String toTemplateKey) {
}
