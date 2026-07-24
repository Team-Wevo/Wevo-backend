package com.wevo.backend.project.service;

import com.wevo.backend.section.domain.ProjectSection;

/**
 * section 존재와 검증된 project 소속이 일치함을 확인한 불변 증거.
 *
 * <p>{@link SectionAccessGuard}만 생성할 수 있어 내부 read service가 section 소속 검증을
 * 호출측 관례에 맡기지 않으면서도 section/issue 도메인 사이의 순환 의존을 만들지 않는다.
 */
public final class VerifiedSectionAccess {

    private final Long projectId;
    private final Long sectionId;

    private VerifiedSectionAccess(Long projectId, Long sectionId) {
        this.projectId = projectId;
        this.sectionId = sectionId;
    }

    static VerifiedSectionAccess of(ProjectSection section) {
        return new VerifiedSectionAccess(section.getProject().getId(), section.getId());
    }

    public Long projectId() {
        return projectId;
    }

    public Long sectionId() {
        return sectionId;
    }
}
