package com.wevo.backend.ai.dto.model;

/**
 * 의견 내용 가드레일 판정. {@code acceptable} 이 false 면 {@code reason} 이 거부 사유를 담는다.
 * (OK / GIBBERISH / OFF_TOPIC — {@code OpinionGuardrailContract})
 */
public record OpinionGuardrailVerdict(boolean acceptable, String reason) {
}
