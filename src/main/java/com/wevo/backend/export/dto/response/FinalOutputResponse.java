package com.wevo.backend.export.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import java.util.List;

/**
 * 최종 결과물 조회 응답.
 *
 * <p><b>완성본은 모든 기본 섹션이 확정된 뒤에만 제공한다</b> — 정책서 §2.3
 * 미확정 섹션이 하나라도 있으면 {@code ready=false} 와 진행도만 반환하고 {@code sections} 는 담지 않는다.
 *
 * @param sections 확정본 목록 — {@code ready=true} 일 때만. 그 외에는 {@code null} 로 두어 직렬화에서 제외된다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FinalOutputResponse(
        Long projectId,
        String title,
        OutputType resultType,
        boolean ready,
        int confirmedCount,
        int totalCount,
        List<SectionOutput> sections
) {

    /**
     * 아직 모든 섹션이 확정되지 않은 상태 — 진행도만 반환한다. (ready = false)
     */
    public static FinalOutputResponse notReady(Project project, int confirmedCount, int totalCount) {
        return new FinalOutputResponse(
                project.getId(), project.getTitle(), project.getResultType(),
                false, confirmedCount, totalCount, null);
    }

    /**
     * 전 섹션이 확정된 상태 — 확정본을 섹션 순서대로 담아 반환한다. (ready = true)
     */
    public static FinalOutputResponse ready(Project project, List<SectionOutput> sections) {
        return new FinalOutputResponse(
                project.getId(), project.getTitle(), project.getResultType(),
                true, sections.size(), sections.size(), sections);
    }

    /**
     * 확정된 섹션 하나의 결과물.
     *
     * @param order   섹션 순서 (`sectionOrder`)
     * @param title   섹션 제목
     * @param content 확정본 본문 — 섹션의 {@code confirmedVersion} 에 해당하는 초안
     */
    public record SectionOutput(Integer order, String title, String content) {
    }
}
