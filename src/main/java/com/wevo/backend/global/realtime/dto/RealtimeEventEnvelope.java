package com.wevo.backend.global.realtime.dto;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * 모든 실시간 도메인 알림이 공유하는 envelope.
 *
 * @param eventId 재연결·중복 수신 시 클라이언트가 이벤트를 식별하는 ID
 * @param eventType UPPER_SNAKE_CASE 이벤트 타입
 * @param payloadVersion 이벤트 타입별 payload 스키마 버전(1부터 시작)
 * @param projectId 이벤트가 속한 프로젝트 ID
 * @param sectionId 섹션 범위 이벤트의 섹션 ID, 프로젝트 범위이면 {@code null}
 * @param occurredAt Asia/Seoul 기준 오프셋 없는 발생 시각
 * @param payload 기능 도메인이 정의한 payload
 */
public record RealtimeEventEnvelope<T>(
        UUID eventId,
        String eventType,
        int payloadVersion,
        Long projectId,
        Long sectionId,
        LocalDateTime occurredAt,
        T payload
) {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 공통 식별 필드와 payload version이 이벤트 계약을 만족하는지 생성 시점에 검증한다.
     */
    public RealtimeEventEnvelope {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        if (eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (payloadVersion < 1) {
            throw new IllegalArgumentException("payloadVersion must be at least 1");
        }
    }

    /**
     * 기능 도메인이 payload version 1 이벤트를 만들 때 공통 ID와 KST 발생 시각을 채운다.
     */
    public static <T> RealtimeEventEnvelope<T> v1(
            String eventType,
            Long projectId,
            Long sectionId,
            T payload
    ) {
        return new RealtimeEventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                1,
                projectId,
                sectionId,
                LocalDateTime.now(KST),
                payload
        );
    }
}
