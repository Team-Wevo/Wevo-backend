package com.wevo.backend.ai.context;

/** 실제 프로젝트 section ID로 해석된 직접 REQUIRES 관계. */
public record ProjectFlowDependencyContext(Long fromSectionId, Long toSectionId) {
    public ProjectFlowDependencyContext {
        if (fromSectionId == null || toSectionId == null || fromSectionId <= 0 || toSectionId <= 0
                || fromSectionId.equals(toSectionId)) {
            throw new IllegalArgumentException("section dependency가 유효하지 않습니다.");
        }
    }
}
