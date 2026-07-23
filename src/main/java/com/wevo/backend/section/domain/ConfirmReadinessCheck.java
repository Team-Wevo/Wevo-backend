package com.wevo.backend.section.domain;

/**
 * 섹션 확정(§6.3) 가능 여부를 구성하는 개별 조건.
 *
 * <ul>
 *   <li>{@link #AI_CHECK_CURRENT}·{@link #MEMBER_APPROVED}·{@link #NO_UNRESOLVED_REQUEST}·
 *       {@link #NO_ACTIVE_EDITOR} — 정책서 §6.3.2 표의 문구를 그대로 사용한다.</li>
 *   <li>{@link #SECTION_REVIEWING} — §6.3.2 표에 대응 문구가 없어(정책 공백) 제안한 문구다.</li>
 * </ul>
 *
 * <p>확정 권한(호출자가 OWNER인가-§6.3 조건 5)은 이 조건 목록이 아니라 응답의 {@code canConfirm}에서
 * 다룬다
 */
public enum ConfirmReadinessCheck {

    /** 섹션이 검토({@code REVIEWING}) 단계여야 확정할 수 있다. (제안 문구 ) */
    SECTION_REVIEWING("섹션이 검토 단계가 아닙니다."),

    /** 현재 초안 기준 AI 사전 검토가 {@code CURRENT} 여야 한다. (§6.3 조건 1) */
    AI_CHECK_CURRENT("현재 초안에 대한 AI 사전 검토가 필요합니다."),

    /** 팀원 중 1명 이상이 동의(APPROVED)해야 한다. 1인 프로젝트는 예외로 충족 처리한다. (§6.3 조건 2·§6.3.1) */
    MEMBER_APPROVED("팀원 1명 이상의 동의가 필요합니다."),

    /** 미해결 수정요청이 0건이어야 한다. (§6.3 조건 3) */
    NO_UNRESOLVED_REQUEST("미해결 수정요청이 있습니다."),

    /** 활성 편집자가 없어야 한다. (§6.3 조건 4) */
    NO_ACTIVE_EDITOR("현재 초안을 편집 중인 사용자가 있습니다.");

    private final String unsatisfiedReason;

    ConfirmReadinessCheck(String unsatisfiedReason) {
        this.unsatisfiedReason = unsatisfiedReason;
    }

    /**
     * 이 조건이 미충족일 때 사용자에게 노출할 사유 문구. (정책서 §6.3.2)
     */
    public String unsatisfiedReason() {
        return unsatisfiedReason;
    }
}
