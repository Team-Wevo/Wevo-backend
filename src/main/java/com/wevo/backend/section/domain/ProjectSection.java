package com.wevo.backend.section.domain;

import com.wevo.backend.global.common.BaseTimeEntity;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.domain.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프로젝트(보드)에 속한 개별 섹션. 의견 수집·초안·검토·확정의 단위.
 *
 * <p><b>Overlay 플래그 (CLAUDE.md §5.7)</b> — {@code status}(섹션 5단계)와 <b>독립</b>이며
 * 한 섹션이 동시에 여러 개를 가질 수 있다. {@code status} 안에 넣지 말 것.
 * <ul>
 *   <li>{@link #getDriftStatus() driftStatus} — 상위 섹션 변경으로 재검토 필요 (§6.4)</li>
 *   <li>{@link #getAiCheckStatus() aiCheckStatus} — AI 사전 검토 유효성. 성공한 검토가 없으면 null (§5.3.3)</li>
 *   <li>{@link #isSynthesisStale() synthesisStale} — 재정리 필요. enum 아님 (§4.5·§5.1.6)</li>
 * </ul>
 * {@code confirmedVersion}은 overlay가 아니라 <b>확정된 본문 버전 필드</b>다(미확정 0).
 * 화면 표시 단계({@code displayStatus})는 저장하지 않는다 — FE가 status+overlay에서 파생한다.
 */
@Entity
@Table(name = "project_sections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectSection extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private SectionTemplate template;

    @Column(length = 200)
    private String title;

    @Column(name = "section_order")
    private Integer sectionOrder;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ProjectSectionStatus status;

    @Column(name = "confirmed_version")
    private Integer confirmedVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "drift_status", length = 30)
    private DriftStatus driftStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_check_status", length = 30)
    private AiCheckStatus aiCheckStatus;

    // 클래스 @Getter가 nullable Boolean을 그대로 노출하지 않도록 막는다 — 조회는 null→false
    // 보정이 있는 isSynthesisStale()로만 한다.
    @Getter(AccessLevel.NONE)
    @Column(name = "synthesis_stale")
    private Boolean synthesisStale;

    @Builder
    private ProjectSection(Project project, SectionTemplate template, String title, Integer sectionOrder,
                           ProjectSectionStatus status, Integer confirmedVersion) {
        this.project = project;
        this.template = template;
        this.title = title;
        this.sectionOrder = sectionOrder;
        this.status = status;
        this.confirmedVersion = confirmedVersion == null ? 0 : confirmedVersion;
        this.driftStatus = DriftStatus.NONE;
        this.synthesisStale = Boolean.FALSE;
    }

    /**
     * 상태를 전이한다. 허용되지 않은 전이면 예외를 던진다. (전이 규칙은 {@link ProjectSectionStatus} 소유)
     *
     * @throws BusinessException 현재 상태에서 {@code target} 으로 전이가 불가능한 경우
     *                           ({@code INVALID_SECTION_STATUS_TRANSITION})
     */
    public void changeStatus(ProjectSectionStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        }
        this.status = target;
    }

    /**
     * 확정된 본문 버전. overlay 도입 전에 생성된 레거시 행(컬럼 null)은 미확정(0)으로 본다.
     */
    public Integer getConfirmedVersion() {
        return confirmedVersion == null ? 0 : confirmedVersion;
    }

    /**
     * 드리프트 상태. 레거시 행(컬럼 null)은 영향 없음({@link DriftStatus#NONE})으로 본다.
     */
    public DriftStatus getDriftStatus() {
        return driftStatus == null ? DriftStatus.NONE : driftStatus;
    }

    /**
     * 재정리 필요 여부. 레거시 행(컬럼 null)은 최신(false)으로 본다.
     */
    public boolean isSynthesisStale() {
        return Boolean.TRUE.equals(synthesisStale);
    }

    /**
     * 직접 의존하는 상위 섹션이 수정됐다 — 재검토 필요 표시. (§6.4 드리프트 전파, API_SPEC §3.7.2)
     */
    public void markDriftReviewRequired() {
        this.driftStatus = DriftStatus.REVIEW_REQUIRED;
    }

    /**
     * 드리프트 해소 — 확정(confirm) 성공 시 {@link DriftStatus#NONE} 복귀. (API_SPEC §3.7.6)
     */
    public void clearDrift() {
        this.driftStatus = DriftStatus.NONE;
    }

    /**
     * 본문이 직접 수정됐다 — 성공한 사전 검토가 있었다면 낡음 처리. (§5.3.3)
     *
     * <p>검토 이력이 없으면(null) 그대로 둔다 — "검토가 없는데 낡음"이라는 상태를 만들지 않는다.
     * 상위 섹션 변경은 이 메서드가 아니라 {@link #markDriftReviewRequired()}가 담당한다(층위 구분).
     */
    public void markAiCheckOutdated() {
        if (this.aiCheckStatus == AiCheckStatus.CURRENT) {
            this.aiCheckStatus = AiCheckStatus.OUTDATED;
        }
    }

    /**
     * 사전 검토가 현재 본문 기준으로 성공했다 — 최신으로 바인딩.
     * (검토 성공, 수정안 적용 후 재바인딩 — API_SPEC §3.8.4)
     */
    public void bindCurrentAiCheck() {
        this.aiCheckStatus = AiCheckStatus.CURRENT;
    }

    /**
     * 재오픈으로 기존 AI 정리 결과가 낡았다 — 재정리 필요 표시. (§4.5, API_SPEC §3.4.6)
     *
     * <p>호출측은 <b>기존 정리 결과가 있을 때만</b> 호출해야 한다 — 정리 실행 전 재오픈이면
     * stale해질 대상이 없으므로 false를 유지한다(§3.4.6 확정 계약).
     */
    public void markSynthesisStale() {
        this.synthesisStale = Boolean.TRUE;
    }

    /**
     * AI 재정리 완료 — 재정리 필요 해제. (API_SPEC §3.8.1)
     */
    public void clearSynthesisStale() {
        this.synthesisStale = Boolean.FALSE;
    }

    /**
     * 섹션 확정 — 확정된 본문 버전을 기록한다. (§6.3, API_SPEC §3.7.6 — 확정 전이와 함께 호출)
     */
    public void recordConfirmedVersion(int contentVersion) {
        if (contentVersion <= 0) {
            throw new IllegalArgumentException("확정 본문 버전은 1 이상이어야 합니다.");
        }
        this.confirmedVersion = contentVersion;
    }
}
