package com.wevo.backend.opinion.dto.response;

import com.wevo.backend.opinion.domain.Opinion;
import com.wevo.backend.opinion.domain.OpinionStatus;
import java.time.LocalDateTime;

/**
 * 내 의견(작업본) 조회 응답. (API_SPEC §3.4.1 — 재제출 모델)
 *
 * <p>의견 미작성은 에러가 아니라 {@code exists=false} 로 표현한다 —
 * 화면 초기 진입 시 "작성 전" 상태를 그려야 하기 때문.
 *
 * @param content               내 작업본 — 임시저장으로 갱신되는 본문 (재편집 중에는 제출본과 다를 수 있다)
 * @param hasUnsubmittedChanges 제출 후 재편집으로 작업본이 제출본과 다른지 (재제출 필요 힌트)
 * @param submittedAt           최초 제출 시각 (§4.3 제출 이력의 근거) — 미제출이면 null
 */
public record MyOpinionResponse(
        boolean exists,
        Long id,
        String content,
        OpinionStatus status,
        Boolean hasUnsubmittedChanges,
        LocalDateTime submittedAt,
        LocalDateTime updatedAt
) {

    public static MyOpinionResponse from(Opinion opinion) {
        return new MyOpinionResponse(
                true,
                opinion.getId(),
                opinion.getContent(),
                opinion.getStatus(),
                opinion.hasUnsubmittedChanges(),
                opinion.getSubmittedAt(),
                opinion.getUpdatedAt()
        );
    }

    public static MyOpinionResponse empty() {
        return new MyOpinionResponse(false, null, null, null, null, null, null);
    }
}
