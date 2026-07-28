package com.wevo.backend.section.service;

/** 검증된 AI 사전 검토 rewrite를 새 draft version으로 append하는 명령. */
public record AiRewriteDraftCreateCommand(
        Long sectionId,
        Long actorUserId,
        int expectedBaseVersion,
        String content
) {
}
