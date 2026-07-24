package com.wevo.backend.ai.context;

import java.util.Arrays;
import java.util.List;

/**
 * 실제 Provider 요청을 구성하는 system/user/schema/context 문자열 조각.
 *
 * <p>구분자나 JSON 문법도 실제 렌더링된 문자열에 포함해 전달해야 한다.</p>
 */
public record AiTokenBudgetInput(List<String> segments) {

    public AiTokenBudgetInput {
        if (segments == null || segments.isEmpty() || segments.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("AI token 계산 입력은 하나 이상의 null이 아닌 조각이어야 합니다.");
        }
        segments = List.copyOf(segments);
    }

    public static AiTokenBudgetInput of(String... segments) {
        return new AiTokenBudgetInput(Arrays.asList(segments));
    }
}
