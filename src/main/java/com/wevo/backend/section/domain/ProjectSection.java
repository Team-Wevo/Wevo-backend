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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
@Table(
        name = "project_sections",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_project_sections_project_order",
                        columnNames = {"project_id", "section_order"}
                ),
                @UniqueConstraint(
                        name = "uk_project_sections_project_template",
                        columnNames = {"project_id", "template_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectSection extends BaseTimeEntity {

    /** 시간 값은 배포 서버 시간대와 무관하게 KST로 고정한다. (CLAUDE.md §5.4) */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private SectionTemplate template;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(name = "section_order", nullable = false)
    private Integer sectionOrder;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private ProjectSectionStatus status;

    @Column(name = "confirmed_version", nullable = false)
    private Integer confirmedVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "drift_status", length = 30, nullable = false)
    private DriftStatus driftStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_check_status", length = 30)
    private AiCheckStatus aiCheckStatus;

    // 클래스 @Getter가 nullable Boolean을 그대로 노출하지 않도록 막는다 — 조회는 null→false
    // 보정이 있는 isSynthesisStale()로만 한다.
    @Getter(AccessLevel.NONE)
    @Column(name = "synthesis_stale", nullable = false)
    private Boolean synthesisStale;

    /**
     * 의견 수집 재오픈 세대. 재오픈 성공마다 증가하며 synthesis 입력 스냅샷에 포함한다.
     * 제출 의견과 GAP 답변이 같아도 이전 마감의 성공 작업을 잘못 재사용하지 않게 하는 경계값이다.
     */
    @Column(name = "opinion_gate_generation", nullable = false)
    private long opinionGateGeneration;

    /**
     * 이 섹션에서 사람이 무언가를 한 마지막 시각. (API_SPEC §3.2.2 — 목록의 마지막 활동 섹션·정렬 기준)
     *
     * <p>{@code updatedAt} 과 다르다. 초안 본문은 {@code section_drafts} 에 따로 쌓이므로 팀원이
     * 초안을 고쳐도 이 행의 {@code updatedAt} 은 움직이지 않고, 반대로 아무도 손대지 않은 섹션이
     * 내부 플래그(드리프트·AI 검토 상태) 변경만으로 갱신되기도 한다. 사용자가 "여기까지 하다 말았다"
     * 라고 느끼는 지점을 그대로 담기 위해 별도 값으로 둔다.
     *
     * <p><b>AI가 돌린 작업은 활동으로 세지 않는다</b> — 자리를 비운 사이 카드가 바뀌면
     * "내가 마지막에 만진 곳"이라는 기대와 어긋난다.
     */
    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

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
        this.opinionGateGeneration = 0;
        // 활동이 한 번도 없어도 값이 비지 않게 생성 시각으로 시작한다 — 목록이 빈 값을 분기하지
        // 않아도 되고, 갓 만든 프로젝트도 "방금 만든 순서"로 정렬된다.
        this.lastActivityAt = LocalDateTime.now(KST);
    }

    /**
     * 상태를 전이하고 그 시각을 활동으로 기록한다.
     * (전이 규칙은 {@link ProjectSectionStatus} 소유)
     *
     * <p>시각을 <b>인자로 받는 이유</b>는 상태 전이를 활동 기록과 떼어놓을 수 없게 하기 위해서다.
     * 호출부가 하나라도 기록을 빠뜨리면 마감·확정 같은 큰 사건이 목록에 반영되지 않는다.
     *
     * @param at 전이 시각 (KST — {@code CLAUDE.md §5.4})
     * @throws BusinessException 현재 상태에서 {@code target} 으로 전이가 불가능한 경우
     *                           ({@code INVALID_SECTION_STATUS_TRANSITION})
     */
    public void changeStatus(ProjectSectionStatus target, LocalDateTime at) {
        if (!this.status.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.INVALID_SECTION_STATUS_TRANSITION);
        }
        this.status = target;
        recordActivity(at);
    }

    /**
     * 상태 전이를 동반하지 않는 활동을 기록한다. (초안 저장, 의견 제출 등)
     *
     * <p>이미 더 나중 시각이 기록돼 있으면 되돌리지 않는다 — 같은 트랜잭션에서 여러 활동이
     * 겹치거나 지연 처리가 뒤늦게 들어와도 마지막 활동 시각이 과거로 밀리지 않게 한다.
     *
     * @param at 활동 시각 (KST — {@code CLAUDE.md §5.4})
     */
    public void recordActivity(LocalDateTime at) {
        if (at == null || (this.lastActivityAt != null && at.isBefore(this.lastActivityAt))) {
            return;
        }
        this.lastActivityAt = at;
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
     * AI 재정리 완료 또는 정리 이력 없음 확인 — 재정리 필요 해제. (API_SPEC §3.8.1)
     */
    public void clearSynthesisStale() {
        this.synthesisStale = Boolean.FALSE;
    }

    /** 의견 수집 재오픈을 새 세대로 기록한다. */
    public void advanceOpinionGateGeneration() {
        this.opinionGateGeneration = Math.addExact(this.opinionGateGeneration, 1L);
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
