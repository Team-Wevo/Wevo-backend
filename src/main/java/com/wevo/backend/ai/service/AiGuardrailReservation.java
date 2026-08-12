package com.wevo.backend.ai.service;

import java.util.List;

/** DB job 생성 실패 시 같은 Redis 원자 예약을 보상하기 위한 불투명 token. */
public record AiGuardrailReservation(
        boolean active,
        String ledgerKey,
        List<String> counterKeys,
        String pendingIndexKey
) {

    public AiGuardrailReservation {
        counterKeys = counterKeys == null ? List.of() : List.copyOf(counterKeys);
    }

    public static AiGuardrailReservation disabled() {
        return new AiGuardrailReservation(false, null, List.of(), null);
    }
}
