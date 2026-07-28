package com.wevo.backend.ai.context;

/** 초안 생성 요청 시점의 최신 본문. 최초 생성이면 version 0, content null이다. */
public record AiBaseDraftContext(
        int version,
        String content
) {
}
