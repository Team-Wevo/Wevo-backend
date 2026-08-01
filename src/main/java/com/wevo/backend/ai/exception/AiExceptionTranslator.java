package com.wevo.backend.ai.exception;

@FunctionalInterface
public interface AiExceptionTranslator {

    AiProviderException translate(Throwable throwable);
}
