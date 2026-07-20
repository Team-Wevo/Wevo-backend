package com.wevo.backend.global.realtime.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.util.JacksonUtils;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealtimeEventEnvelopeTest {

    private final JsonMapper jsonMapper = JacksonUtils.getDefaultJsonMapper();

    @Test
    @DisplayName("공통 envelope를 payload version과 KST 계약 형식으로 직렬화한다")
    void serialize_containsCommonEnvelopeFields() throws Exception {
        UUID eventId = UUID.fromString("c94cc5d9-d7a5-4be1-967f-1b8d56296990");
        RealtimeEventEnvelope<Map<String, Object>> envelope = new RealtimeEventEnvelope<>(
                eventId,
                "DRAFT_LEASE_CHANGED",
                1,
                10L,
                20L,
                LocalDateTime.of(2026, 7, 20, 14, 30),
                Map.of("locked", true)
        );

        String json = jsonMapper.writeValueAsString(envelope);

        assertThat(json).contains(
                "\"eventId\":\"c94cc5d9-d7a5-4be1-967f-1b8d56296990\"",
                "\"eventType\":\"DRAFT_LEASE_CHANGED\"",
                "\"payloadVersion\":1",
                "\"projectId\":10",
                "\"sectionId\":20",
                "\"occurredAt\":\"2026-07-20T14:30:00\"",
                "\"payload\":{\"locked\":true}"
        );
    }

    @Test
    @DisplayName("payload version은 1 이상이어야 한다")
    void construct_withInvalidPayloadVersion_rejects() {
        assertThatThrownBy(() -> new RealtimeEventEnvelope<>(
                UUID.randomUUID(),
                "DRAFT_LEASE_CHANGED",
                0,
                10L,
                20L,
                LocalDateTime.now(),
                Map.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payloadVersion");
    }
}
