package com.wevo.backend.ai.domain;

public enum AiJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    STALE
}
