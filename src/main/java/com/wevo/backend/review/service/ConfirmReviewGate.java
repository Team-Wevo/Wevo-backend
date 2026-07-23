package com.wevo.backend.review.service;

/**
 * 섹션 확정(§6.3)의 <b>팀 검토 관련 조건</b> 판정 결과. (확정 가능 여부 조회 §3.7.5, 확정 §3.7.6)
 *
 * <p>두 조건 모두 같은 팀 검토 목록에서 파생되므로, 목록을 한 번만 조회해 함께 판정한다
 * ({@link TeamReviewService#evaluateConfirmReviewGate}). 값은 <b>충족 여부(positive)</b>로 표현해
 * 호출측이 부정 연산 없이 그대로 쓸 수 있게 한다.
 *
 * @param memberApprovalSatisfied   팀원 동의 조건 충족 여부 (§6.3 조건 2·§6.3.1 1인 예외)
 * @param noUnresolvedChangeRequest 미해결 수정요청이 없는지 (§6.3 조건 3)
 */
public record ConfirmReviewGate(
        boolean memberApprovalSatisfied,
        boolean noUnresolvedChangeRequest
) {
}
