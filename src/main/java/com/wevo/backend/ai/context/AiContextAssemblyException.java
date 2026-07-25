package com.wevo.backend.ai.context;

/** 원문 입력을 노출하지 않는 내부 AI context 조립 실패. */
public class AiContextAssemblyException extends IllegalStateException {

    public AiContextAssemblyException(Throwable cause) {
        super("AI 작업에 필요한 section context를 조립할 수 없습니다.", cause);
    }
}
