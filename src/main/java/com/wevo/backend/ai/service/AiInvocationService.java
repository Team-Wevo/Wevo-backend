package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.client.ClaudeGateway;
import com.wevo.backend.ai.client.ClaudeRequest;
import com.wevo.backend.ai.client.ClaudeResponse;
import com.wevo.backend.ai.client.StructuredClaudeRequest;
import com.wevo.backend.ai.client.StructuredClaudeResponse;
import com.wevo.backend.ai.exception.AiAuditPersistenceException;
import com.wevo.backend.ai.exception.ClaudeProviderException;
import org.springframework.stereotype.Service;

@Service
public class AiInvocationService {

    private final ClaudeGateway claudeGateway;
    private final AiUsageService usageService;

    public AiInvocationService(ClaudeGateway claudeGateway, AiUsageService usageService) {
        this.claudeGateway = claudeGateway;
        this.usageService = usageService;
    }

    public <T> AiInvocationResult<T> invoke(
            AiUsageStartCommand startCommand,
            ClaudeRequest request,
            AiResultHandler<T> resultHandler
    ) {
        if (startCommand.feature() != request.feature()) {
            throw new IllegalArgumentException("감사 로그와 Claude 요청의 AI 기능이 일치해야 합니다.");
        }

        AiUsageHandle handle = usageService.startRequest(startCommand);
        ClaudeResponse response = null;
        AiProcessedResult<T> processedResult;

        try {
            response = claudeGateway.generate(request);
            processedResult = resultHandler.process(response);
            if (processedResult == null) {
                throw new IllegalStateException("AI 결과 처리 결과는 null일 수 없습니다.");
            }
        } catch (RuntimeException exception) {
            AiUsageMetadata usage = response == null ? null : response.usageMetadata();
            Integer attemptCount = response == null ? null : response.attemptCount();
            if (response == null && exception instanceof ClaudeProviderException providerException) {
                usage = providerException.getUsageMetadata();
                attemptCount = providerException.getAttemptCount() > 0
                        ? providerException.getAttemptCount()
                        : null;
            }
            try {
                usageService.completeFailure(handle, exception, usage, attemptCount);
            } catch (AiAuditPersistenceException auditException) {
                auditException.addSuppressed(exception);
                throw auditException;
            }
            throw exception;
        }

        usageService.completeSuccess(
                handle,
                response.usageMetadata(),
                response.attemptCount(),
                processedResult.resultId()
        );
        return new AiInvocationResult<>(processedResult.value(), handle.logId(), handle.requestId());
    }

    public <I, O> AiInvocationResult<O> invokeStructured(
            AiUsageStartCommand startCommand,
            StructuredClaudeRequest<I> request,
            StructuredAiResultHandler<I, O> resultHandler
    ) {
        if (startCommand.feature() != request.feature()) {
            throw new IllegalArgumentException("감사 로그와 Claude 요청의 AI 기능이 일치해야 합니다.");
        }
        if (!startCommand.promptVersion().equals(request.prompt().trackingVersion())) {
            throw new IllegalArgumentException("감사 로그와 Claude 요청의 prompt version이 일치해야 합니다.");
        }

        AiUsageHandle handle = usageService.startRequest(startCommand);
        StructuredClaudeResponse<I> response = null;
        AiProcessedResult<O> processedResult;

        try {
            response = claudeGateway.generateStructured(request);
            processedResult = resultHandler.process(response);
            if (processedResult == null) {
                throw new IllegalStateException("AI 결과 처리 결과는 null일 수 없습니다.");
            }
        } catch (RuntimeException exception) {
            AiUsageMetadata usage = response == null ? null : response.usageMetadata();
            Integer attemptCount = response == null ? null : response.attemptCount();
            if (response == null && exception instanceof ClaudeProviderException providerException) {
                usage = providerException.getUsageMetadata();
                attemptCount = providerException.getAttemptCount() > 0
                        ? providerException.getAttemptCount()
                        : null;
            }
            try {
                usageService.completeFailure(handle, exception, usage, attemptCount);
            } catch (AiAuditPersistenceException auditException) {
                auditException.addSuppressed(exception);
                throw auditException;
            }
            throw exception;
        }

        usageService.completeSuccess(
                handle,
                response.usageMetadata(),
                response.attemptCount(),
                processedResult.resultId()
        );
        return new AiInvocationResult<>(processedResult.value(), handle.logId(), handle.requestId());
    }
}
