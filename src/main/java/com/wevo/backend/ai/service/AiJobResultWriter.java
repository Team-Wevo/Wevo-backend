package com.wevo.backend.ai.service;

@FunctionalInterface
public interface AiJobResultWriter {

    Long persist();
}
