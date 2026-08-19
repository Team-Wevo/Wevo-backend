package com.wevo.backend.ai.context;

import com.wevo.backend.ai.domain.AiFeature;

/**
 * 의견 내용 가드레일 입력 — 섹션 질문(제목·가이드)과 제출하려는 의견 본문만 담는다.
 * 판정은 "이 본문이 이해 가능한 글이고 섹션 질문과 관련 있는가"다. (개인 식별정보 제외 — §7)
 */
public record OpinionGuardrailContext(
        Long sectionId,
        String sectionTitle,
        String sectionGuide,
        String content
) implements AiFeatureContext {

    @Override
    public AiFeature feature() {
        return AiFeature.OPINION_CONTENT_GUARDRAIL;
    }

    @Override
    public String sourceVersion() {
        // 판정 대상은 제출 시점의 본문 — 본문 자체가 스냅샷 해시에 들어가므로 단일 버전으로 둔다.
        return "opinion-v1";
    }
}
