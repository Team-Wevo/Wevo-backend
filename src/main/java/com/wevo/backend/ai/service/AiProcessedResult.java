package com.wevo.backend.ai.service;

public record AiProcessedResult<T>(T value, Long resultId) {
}
