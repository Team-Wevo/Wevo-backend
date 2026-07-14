package com.wevo.backend.review.dto.request;

import com.wevo.backend.review.domain.TeamReviewStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 팀원(MEMBER)의 팀 검토 제출 요청. (1인 1검토 업서트)
 *
 * <p>제출 가능한 상태는 {@code APPROVED}(동의) 또는 {@code CHANGES_REQUESTED}(수정 요청) 뿐이다.
 * {@code CHANGES_REQUESTED} 는 사유(changeRequestReason)가 필수(≥1자)다.
 *
 * @param status              동의 여부 (APPROVED / CHANGES_REQUESTED)
 * @param changeRequestReason 수정 요청 사유 — CHANGES_REQUESTED 필수, APPROVED 시 무시
 */
public record TeamReviewSubmitRequest(
        @NotNull TeamReviewStatus status,
        @Size(max = 1000) String changeRequestReason
) {

    /** 제출 가능한 상태는 APPROVED / CHANGES_REQUESTED 뿐이다. (PENDING 제출 불가) */
    @AssertTrue(message = "APPROVED 또는 CHANGES_REQUESTED 만 제출할 수 있습니다.")
    public boolean isSubmittableStatus() {
        return status == null
                || status == TeamReviewStatus.APPROVED
                || status == TeamReviewStatus.CHANGES_REQUESTED;
    }

    /** 수정 요청(CHANGES_REQUESTED)이면 사유가 있어야 한다. */
    @AssertTrue(message = "수정 요청 사유를 입력해주세요.")
    public boolean isReasonPresentForChangesRequested() {
        if (status != TeamReviewStatus.CHANGES_REQUESTED) {
            return true;
        }
        return changeRequestReason != null && !changeRequestReason.isBlank();
    }
}
