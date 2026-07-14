package com.wevo.backend.ai.client;

public class StructuredOutputSemanticException extends RuntimeException {

    public StructuredOutputSemanticException() {
        super("AI 구조화 출력의 의미 검증에 실패했습니다.");
    }
}
