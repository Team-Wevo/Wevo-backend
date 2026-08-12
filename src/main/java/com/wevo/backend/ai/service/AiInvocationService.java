package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.AiUsageMetadata;
import com.wevo.backend.ai.client.AiProviderExecutionPolicy;
import com.wevo.backend.ai.client.AiProviderGateway;
import com.wevo.backend.ai.client.AiProviderRequest;
import com.wevo.backend.ai.client.AiProviderResponse;
import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.client.StructuredAiProviderResponse;
import com.wevo.backend.ai.exception.AiAuditPersistenceException;
import com.wevo.backend.ai.exception.AiProviderException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.operations.AiExecutionControl;
import com.wevo.backend.ai.operations.AiOperationalMetrics;

import java.time.Duration;

@Service
@ConditionalOnBean(AiProviderGateway.class)
public class AiInvocationService {

    private final AiProviderGateway providerGateway;
    private final AiUsageService usageService;
    private AiExecutionControl executionControl;
    private AiOperationalMetrics metrics;
    private AiProperties properties;

    public AiInvocationService(AiProviderGateway providerGateway, AiUsageService usageService) {
        this.providerGateway = providerGateway;
        this.usageService = usageService;
    }

    public <T> AiInvocationResult<T> invoke(
            AiUsageStartCommand startCommand,
            AiProviderRequest request,
            AiResultHandler<T> resultHandler
    ) {
        if (startCommand.feature() != request.feature()) {
            throw new IllegalArgumentException("감사 로그와 Provider 요청의 AI 기능이 일치해야 합니다.");
        }

        AiUsageHandle handle = usageService.startRequest(startCommand);
        request = withExecutionPolicy(request, startCommand);
        AiProviderResponse response = null;
        AiProcessedResult<T> processedResult;
        long providerStarted = 0L;

        try {
            beforeProviderCall(startCommand);
            usageService.markProviderStarted(startCommand.aiJob());
            providerStarted = System.nanoTime();
            response = providerGateway.generate(request);
            recordProviderLatency(startCommand, response.usageMetadata(), providerStarted);
            recordRetries(startCommand, response.usageMetadata(),
                    Math.max(0, response.attemptCount() - 1), 0);
            recordProviderSuccess();
            processedResult = resultHandler.process(response);
            if (processedResult == null) {
                throw new IllegalStateException("AI 결과 처리 결과는 null일 수 없습니다.");
            }
        } catch (RuntimeException exception) {
            if (response == null && providerStarted != 0L) {
                recordProviderLatency(startCommand, providerUsage(exception), providerStarted);
            }
            recordProviderFailure(exception);
            AiUsageMetadata usage = response == null ? null : response.usageMetadata();
            Integer attemptCount = response == null ? null : response.attemptCount();
            if (response == null && exception instanceof AiProviderException providerException) {
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
            StructuredAiProviderRequest<I> request,
            StructuredAiResultHandler<I, O> resultHandler
    ) {
        if (startCommand.feature() != request.feature()) {
            throw new IllegalArgumentException("감사 로그와 Provider 요청의 AI 기능이 일치해야 합니다.");
        }
        if (!startCommand.promptVersion().equals(request.prompt().trackingVersion())) {
            throw new IllegalArgumentException("감사 로그와 Provider 요청의 prompt version이 일치해야 합니다.");
        }

        AiUsageHandle handle = usageService.startRequest(startCommand);
        request = withExecutionPolicy(request, startCommand);
        StructuredAiProviderResponse<I> response = null;
        AiProcessedResult<O> processedResult;
        long providerStarted = 0L;

        try {
            beforeProviderCall(startCommand);
            usageService.markProviderStarted(startCommand.aiJob());
            providerStarted = System.nanoTime();
            response = providerGateway.generateStructured(request);
            recordProviderLatency(startCommand, response.usageMetadata(), providerStarted);
            recordRetries(startCommand, response.usageMetadata(),
                    response.providerRetryCount(), response.correctionRetryCount());
            recordProviderSuccess();
            processedResult = resultHandler.process(response);
            if (processedResult == null) {
                throw new IllegalStateException("AI 결과 처리 결과는 null일 수 없습니다.");
            }
        } catch (RuntimeException exception) {
            if (response == null && providerStarted != 0L) {
                recordProviderLatency(startCommand, providerUsage(exception), providerStarted);
            }
            recordProviderFailure(exception);
            AiUsageMetadata usage = response == null ? null : response.usageMetadata();
            Integer attemptCount = response == null ? null : response.attemptCount();
            if (response == null && exception instanceof AiProviderException providerException) {
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

    @Autowired(required = false)
    void setExecutionControl(AiExecutionControl executionControl) {
        this.executionControl = executionControl;
    }

    @Autowired(required = false)
    void setMetrics(AiOperationalMetrics metrics) {
        this.metrics = metrics;
    }

    @Autowired(required = false)
    void setProperties(AiProperties properties) {
        this.properties = properties;
    }

    private void beforeProviderCall(AiUsageStartCommand command) {
        if (executionControl != null) {
            executionControl.beforeProviderCall(command.feature());
        }
    }

    private void recordProviderSuccess() {
        if (executionControl != null) {
            executionControl.recordSuccess();
        }
    }

    private void recordProviderFailure(Throwable throwable) {
        if (executionControl != null) {
            executionControl.recordFailure(throwable);
        }
    }

    private void recordProviderLatency(
            AiUsageStartCommand command,
            AiUsageMetadata usage,
            long startedNanos
    ) {
        if (metrics == null) {
            return;
        }
        String provider = usage != null && usage.providerId() != null
                ? usage.providerId() : properties == null ? "unknown" : properties.provider();
        String model = command.aiJob() == null
                ? usage == null ? null : usage.modelId()
                : command.aiJob().getModelId();
        metrics.recordProviderLatency(command.feature(), provider, model,
                Duration.ofNanos(Math.max(0L, System.nanoTime() - startedNanos)));
    }

    private AiProviderRequest withExecutionPolicy(
            AiProviderRequest request,
            AiUsageStartCommand command
    ) {
        if (command.aiJob() == null) {
            return request;
        }
        return request.withExecutionPolicy(new AiProviderExecutionPolicy(
                command.aiJob().getModelId(), command.aiJob().getMaxOutputTokens(),
                command.aiJob().getReasoningEffort()));
    }

    private <T> StructuredAiProviderRequest<T> withExecutionPolicy(
            StructuredAiProviderRequest<T> request,
            AiUsageStartCommand command
    ) {
        if (command.aiJob() == null) {
            return request;
        }
        return request.withExecutionPolicy(new AiProviderExecutionPolicy(
                command.aiJob().getModelId(), command.aiJob().getMaxOutputTokens(),
                command.aiJob().getReasoningEffort()));
    }

    private AiUsageMetadata providerUsage(Throwable throwable) {
        return throwable instanceof AiProviderException exception
                ? exception.getUsageMetadata() : null;
    }

    private void recordRetries(
            AiUsageStartCommand command,
            AiUsageMetadata usage,
            int transportRetries,
            int correctionRetries
    ) {
        if (metrics == null) {
            return;
        }
        String provider = usage != null && usage.providerId() != null
                ? usage.providerId() : properties == null ? "unknown" : properties.provider();
        String model = command.aiJob() == null
                ? usage == null ? null : usage.modelId()
                : command.aiJob().getModelId();
        metrics.recordRetries(command.feature(), provider, model, transportRetries, correctionRetries);
    }
}
