package com.wevo.backend.project.dto.response;

import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.section.service.SectionConfirmationSummary;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 프로젝트 상세. 내 역할·멤버 수·섹션 진행 요약을 함께 제공한다.
 *
 * <p>{@code description} 만 필수에서 뺐다 — 생성 시에는 입력받지 않아 수정(§3.2.10) 전까지
 * 값이 없고, {@code null} 이면 응답에서 키 자체가 생략된다(§1.4).
 *
 * @param memberCount     프로젝트 멤버 수
 * @param sectionProgress 확정된 섹션 수 / 전체 섹션 수
 */
@Schema(requiredProperties = {
        "projectId", "title", "ideaText", "resultType", "audience", "status", "myRole", "memberCount",
        "sectionProgress"
})
public record ProjectDetailResponse(
        Long projectId,
        String title,
        String description,
        String ideaText,
        OutputType resultType,
        String audience,
        ProjectStatus status,
        ProjectMemberRole myRole,
        long memberCount,
        SectionProgress sectionProgress
) {

    /**
     * 응답 계약상의 진행도 표현. 값 자체는 {@link SectionConfirmationSummary}(단일 정의)에서
     * 그대로 옮겨 담기만 하며, 여기서 다시 계산하지 않는다. 필드명은 이미 FE 와 합의된
     * 계약이므로 summary 의 이름을 그대로 쓰지 않고 매핑한다.
     *
     * @param total     전체 섹션 수 (유형별 고정 6개)
     * @param confirmed CONFIRMED 상태인 섹션 수
     */
    @Schema(requiredProperties = {"total", "confirmed"})
    public record SectionProgress(int total, int confirmed) {

        static SectionProgress from(SectionConfirmationSummary summary) {
            return new SectionProgress(summary.totalCount(), summary.confirmedCount());
        }
    }

    public static ProjectDetailResponse of(Project project,
                                           ProjectMemberRole myRole,
                                           long memberCount,
                                           SectionConfirmationSummary sectionSummary) {
        return new ProjectDetailResponse(
                project.getId(),
                project.getTitle(),
                project.getDescription(),
                project.getIdeaText(),
                project.getResultType(),
                project.getAudience(),
                project.getStatus(),
                myRole,
                memberCount,
                SectionProgress.from(sectionSummary)
        );
    }
}
