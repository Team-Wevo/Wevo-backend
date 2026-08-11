package com.wevo.backend.ai.client;

import java.util.regex.Pattern;

/** Provider 구조화 호출의 기능 내부 단계. 사용자 입력이 아닌 서버 고정값만 허용한다. */
public record StructuredOutputExecutionContext(String stage, Integer chunkIndex) {

    private static final Pattern STAGE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,49}");

    public StructuredOutputExecutionContext {
        if (stage == null || !STAGE_PATTERN.matcher(stage).matches()) {
            throw new IllegalArgumentException("구조화 출력 실행 단계는 대문자 고정 코드여야 합니다.");
        }
        if (chunkIndex != null && chunkIndex <= 0) {
            throw new IllegalArgumentException("chunkIndex는 1 이상이어야 합니다.");
        }
    }

    public static StructuredOutputExecutionContext unspecified() {
        return new StructuredOutputExecutionContext("UNSPECIFIED", null);
    }
}
