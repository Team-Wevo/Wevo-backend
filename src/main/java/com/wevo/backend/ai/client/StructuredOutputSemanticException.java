package com.wevo.backend.ai.client;

public class StructuredOutputSemanticException extends RuntimeException {

    private final StructuredOutputSemanticFailureReason reason;
    private final String field;
    private final Long offendingResourceId;
    private final Integer expectedCount;
    private final Integer actualCount;
    private final StructuredOutputExecutionContext executionContext;

    public StructuredOutputSemanticException() {
        this(StructuredOutputSemanticFailureReason.UNKNOWN, null, null, null, null,
                StructuredOutputExecutionContext.unspecified());
    }

    public StructuredOutputSemanticException(
            StructuredOutputSemanticFailureReason reason,
            String field
    ) {
        this(reason, field, null, null, null, StructuredOutputExecutionContext.unspecified());
    }

    public StructuredOutputSemanticException(
            StructuredOutputSemanticFailureReason reason,
            String field,
            Long offendingResourceId
    ) {
        this(reason, field, offendingResourceId, null, null,
                StructuredOutputExecutionContext.unspecified());
    }

    public StructuredOutputSemanticException(
            StructuredOutputSemanticFailureReason reason,
            String field,
            Long offendingResourceId,
            Integer expectedCount,
            Integer actualCount
    ) {
        this(reason, field, offendingResourceId, expectedCount, actualCount,
                StructuredOutputExecutionContext.unspecified());
    }

    private StructuredOutputSemanticException(
            StructuredOutputSemanticFailureReason reason,
            String field,
            Long offendingResourceId,
            Integer expectedCount,
            Integer actualCount,
            StructuredOutputExecutionContext executionContext
    ) {
        super("AI 구조화 출력의 의미 검증에 실패했습니다.");
        this.reason = reason == null ? StructuredOutputSemanticFailureReason.UNKNOWN : reason;
        this.field = field;
        this.offendingResourceId = offendingResourceId;
        this.expectedCount = expectedCount;
        this.actualCount = actualCount;
        this.executionContext = executionContext == null
                ? StructuredOutputExecutionContext.unspecified()
                : executionContext;
    }

    public StructuredOutputSemanticException withExecutionContext(
            StructuredOutputExecutionContext context
    ) {
        return new StructuredOutputSemanticException(
                reason, field, offendingResourceId, expectedCount, actualCount, context);
    }

    public StructuredOutputSemanticFailureReason getReason() {
        return reason;
    }

    public String getField() {
        return field;
    }

    public Long getOffendingResourceId() {
        return offendingResourceId;
    }

    public Integer getExpectedCount() {
        return expectedCount;
    }

    public Integer getActualCount() {
        return actualCount;
    }

    public StructuredOutputExecutionContext getExecutionContext() {
        return executionContext;
    }
}
