package com.wevo.backend.ai.context;

/** 원문 입력을 예외 메시지에 포함하지 않는 canonical 직렬화 실패. */
public class AiContextSerializationException extends IllegalStateException {

    public AiContextSerializationException(Throwable cause) {
        super("AI context canonical 직렬화에 실패했습니다.", cause);
    }
}
