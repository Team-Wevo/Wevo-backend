package com.wevo.backend.project.dto.response;

import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.project.domain.ProjectMember;
import com.wevo.backend.project.domain.ProjectMemberRole;
import com.wevo.backend.project.domain.ProjectStatus;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import com.wevo.backend.section.service.SectionConfirmationSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 프로젝트 목록 항목. (내가 멤버로 속한 프로젝트)
 *
 * <p>대시보드 카드가 "어디까지 했는지"를 <b>한 번의 요청으로</b> 그릴 수 있도록
 * {@link LastActiveSection} 과 {@link SectionProgress} 를 함께 담는다. 없으면 프로젝트마다
 * 섹션 목록 API 를 따로 불러야 해서, 카드 N개에 요청이 N+1번 나간다.
 *
 * <p>{@code lastActiveSection} 만 필수에서 뺐다 — 섹션 생성이 실패한 비정상 프로젝트에서만
 * {@code null} 이 되며, 그 경우 응답에서 키가 생략된다(§1.4).
 *
 * @param myRole 해당 프로젝트에서의 내 역할
 */
@Schema(requiredProperties = {
        "projectId", "title", "resultType", "status", "myRole", "createdAt", "sectionProgress"
}, example = """
        {
          "projectId": 3,
          "title": "위보 발표 준비",
          "resultType": "PRESENTATION",
          "status": "ACTIVE",
          "myRole": "OWNER",
          "createdAt": "2026-08-01T09:30:00",
          "lastActiveSection": {
            "sectionId": 13,
            "order": 3,
            "title": "해결 방향",
            "sectionStatus": "DRAFTING"
          },
          "sectionProgress": { "total": 6, "confirmed": 2 }
        }""")
public record ProjectSummaryResponse(
        Long projectId,
        String title,
        OutputType resultType,
        ProjectStatus status,
        ProjectMemberRole myRole,
        LocalDateTime createdAt,
        LastActiveSection lastActiveSection,
        SectionProgress sectionProgress
) {

    /**
     * 이 프로젝트에서 <b>가장 마지막에 활동이 있었던 섹션</b>.
     *
     * <p>"지금 진행 중인 단계"가 아니다 — 4번 섹션까지 진행한 뒤 1번을 고치면 1번이 된다.
     * 이름을 {@code currentSection} 이 아니라 {@code lastActiveSection} 으로 둔 이유다.
     *
     * <p>표시 문구("작성 중" 등)는 만들지 않고 {@code sectionStatus} 값만 내려보낸다. 라벨은
     * 화면에서 매핑한다 — 서버가 문장을 만들면 문구를 고칠 때마다 배포가 필요하다.
     * ({@code UnderstandingSignal} 의 UI 라벨을 화면에서 매핑하기로 한 정책서 §8과 같은 방식)
     */
    @Schema(requiredProperties = {"sectionId", "order", "title", "sectionStatus"})
    public record LastActiveSection(
            Long sectionId,
            Integer order,
            String title,
            ProjectSectionStatus sectionStatus
    ) {

        static LastActiveSection from(ProjectSection section) {
            return new LastActiveSection(
                    section.getId(),
                    section.getSectionOrder(),
                    section.getTitle(),
                    section.getStatus());
        }
    }

    /**
     * 섹션 확정 진행도. 상세 조회(§3.2.3)의 {@code sectionProgress} 와 <b>같은 구조</b>이고
     * 값도 같은 {@link SectionConfirmationSummary} 에서 파생한다.
     *
     * <p>{@code total == confirmed} 이면 전 섹션이 확정된 프로젝트라, 화면이 "완성"을 표시하고
     * 그렇지 않으면 진행률(3/6)을 표시할 수 있다.
     */
    @Schema(requiredProperties = {"total", "confirmed"})
    public record SectionProgress(int total, int confirmed) {

        static SectionProgress from(SectionConfirmationSummary summary) {
            return new SectionProgress(summary.totalCount(), summary.confirmedCount());
        }
    }

    /**
     * @param lastActiveSection 마지막 활동 섹션. 섹션은 프로젝트 생성 시 유형별로 전부 만들어지므로
     *                          정상 데이터에서는 항상 존재한다 — {@code null} 은 섹션 생성이 실패한
     *                          비정상 프로젝트뿐이며, 그 경우 필드를 생략한다(§1.4)
     * @param sectionSummary    확정 진행도 — 세는 규칙은 section 도메인이 소유한다
     */
    public static ProjectSummaryResponse of(ProjectMember membership,
                                            ProjectSection lastActiveSection,
                                            SectionConfirmationSummary sectionSummary) {
        Project project = membership.getProject();
        return new ProjectSummaryResponse(
                project.getId(),
                project.getTitle(),
                project.getResultType(),
                project.getStatus(),
                membership.getRole(),
                project.getCreatedAt(),
                lastActiveSection == null ? null : LastActiveSection.from(lastActiveSection),
                SectionProgress.from(sectionSummary)
        );
    }
}
