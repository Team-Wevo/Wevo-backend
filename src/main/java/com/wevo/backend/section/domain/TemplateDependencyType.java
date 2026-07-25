package com.wevo.backend.section.domain;

public enum TemplateDependencyType {
    /** 제품 정책의 직접 {@code dependsOn}. from(하위)이 to(상위)를 필요로 한다. */
    REQUIRES,
    /** MVP 기준 데이터에서는 사용하지 않는 확장 type. */
    BLOCKS,
    /** MVP 기준 데이터에서는 사용하지 않는 확장 type. */
    RECOMMENDS
}
