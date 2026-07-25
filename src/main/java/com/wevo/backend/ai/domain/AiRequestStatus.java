package com.wevo.backend.ai.domain;

public enum AiRequestStatus {
    REQUESTED, // 요청된
    SUCCEEDED, // 성공한
    FAILED;    // 실패한

    /**
     * 내부 작업 상태를 외부 계약 3값으로 접는다. (API_SPEC §3.8)
     *
     * <p>클라이언트는 "기다리면 되는가 / 결과가 있는가 / 다시 실행해야 하는가"만 구분하면 되므로
     * 큐잉·실행 중은 {@code REQUESTED}, 취소·입력 변경 폐기(STALE)는 {@code FAILED}로 함께 접는다.
     * 실패 원인은 {@code failure.errorCode}로 전달한다.
     */
    public static AiRequestStatus from(AiJobStatus status) {
        return switch (status) {
            case QUEUED, RUNNING -> REQUESTED;
            case SUCCEEDED -> SUCCEEDED;
            case FAILED, CANCELLED, STALE -> FAILED;
        };
    }
}
