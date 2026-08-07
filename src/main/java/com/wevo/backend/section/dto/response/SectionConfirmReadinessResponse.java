package com.wevo.backend.section.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.section.domain.ConfirmReadinessCheck;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 섹션 확정 가능 여부 조회 응답.
 *
 * <p>{@code ready}(섹션 조건 충족 여부)와 {@code canConfirm}(호출자 권한까지 포함)을 <b>분리</b>한다.
 * 팀원이 조회하면 조건이 모두 충족돼도({@code ready=true}) 확정 실행은 팀장만 가능하므로
 * {@code canConfirm=false}가 된다.
 *
 * <p>{@code checks}는 조건별 충족 여부와 미충족 사유(§6.3.2)를 담는다 — 충족된 조건의 {@code reason}은
 * {@code null}이라 직렬화에서 생략된다.
 */
@Schema(requiredProperties = {"ready", "canConfirm", "checks"})
public record SectionConfirmReadinessResponse(
        boolean ready,
        boolean canConfirm,
        List<CheckResponse> checks
) {

    public static SectionConfirmReadinessResponse of(boolean canConfirmRole, List<CheckResponse> checks) {
        boolean ready = checks.stream().allMatch(CheckResponse::satisfied);
        return new SectionConfirmReadinessResponse(ready, ready && canConfirmRole, checks);
    }

    /**
     * 개별 확정 조건의 판정 결과.
     *
     * @param key       조건 식별자 ({@link ConfirmReadinessCheck} 이름)
     * @param satisfied 충족 여부
     * @param reason    미충족 사유 (충족이면 {@code null} — 응답에서 생략)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(requiredProperties = {"key", "satisfied"})
    public record CheckResponse(String key, boolean satisfied, String reason) {

        public static CheckResponse of(ConfirmReadinessCheck check, boolean satisfied) {
            return new CheckResponse(
                    check.name(),
                    satisfied,
                    satisfied ? null : check.unsatisfiedReason()
            );
        }
    }
}
