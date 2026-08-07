package com.wevo.backend.project.dto.response;

import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 섹션 요약 응답. (프로젝트 생성/조회 시 섹션 목록에 사용 — B/프론트 계약)
 *
 * <p>{@code keyQuestion}/{@code guide} 는 연결된 SectionTemplate 의 baseline 에서 온다.
 * ({@code description} = 기본 핵심 질문, {@code guideText} = 작성 가이드 — ERD 준수)
 *
 * @param sectionId     섹션 ID (의견·초안 등이 매달리는 기준)
 * @param order         섹션 순서
 * @param title         섹션명
 * @param sectionStatus 섹션 상태 (초기값 COLLECTING) — overlay 플래그(driftStatus 등)와 구분되는
 *                      필드명 (API_SPEC §3.2.1·§3.2.4, CLAUDE.md §5.7)
 * @param keyQuestion   기본 핵심 질문
 * @param guide         작성 가이드
 */
// keyQuestion·guide 는 템플릿이 연결되지 않은 섹션에서 null 이 되므로 필수에서 뺀다.
@Schema(requiredProperties = {"sectionId", "order", "title", "sectionStatus"})
public record SectionSummaryResponse(
        Long sectionId,
        Integer order,
        String title,
        ProjectSectionStatus sectionStatus,
        String keyQuestion,
        String guide
) {

    public static SectionSummaryResponse from(ProjectSection section) {
        return new SectionSummaryResponse(
                section.getId(),
                section.getSectionOrder(),
                section.getTitle(),
                section.getStatus(),
                section.getTemplate() != null ? section.getTemplate().getDescription() : null,
                section.getTemplate() != null ? section.getTemplate().getGuideText() : null
        );
    }
}
