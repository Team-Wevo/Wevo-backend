package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.ai.client.AiProviderGateway;
import com.wevo.backend.ai.client.AiProviderRequest;
import com.wevo.backend.ai.client.AiProviderResponse;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.exception.AiProviderException;
import com.wevo.backend.ai.operations.AiExecutionControl;
import com.wevo.backend.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AiInvocationServiceGuardrailTest {

    private AiProviderGateway gateway;
    private AiUsageService usageService;
    private AiExecutionControl executionControl;
    private AiInvocationService service;
    private AiUsageStartCommand startCommand;
    private AiJob job;
    private AiProviderRequest request;

    @BeforeEach
    void setUp() {
        gateway = mock(AiProviderGateway.class);
        usageService = mock(AiUsageService.class);
        executionControl = mock(AiExecutionControl.class);
        service = new AiInvocationService(gateway, usageService);
        service.setExecutionControl(executionControl);
        startCommand = mock(AiUsageStartCommand.class);
        job = mock(AiJob.class);
        request = new AiProviderRequest(AiFeature.DRAFT_GENERATION, "system", "user");
        given(startCommand.feature()).willReturn(AiFeature.DRAFT_GENERATION);
        given(startCommand.aiJob()).willReturn(job);
        given(job.getModelId()).willReturn("test-model");
        given(job.getMaxOutputTokens()).willReturn(100);
        given(job.getReasoningEffort()).willReturn("medium");
        given(usageService.startRequest(startCommand)).willReturn(new AiUsageHandle(
                1L,
                UUID.randomUUID(),
                job,
                AiFeature.DRAFT_GENERATION,
                "openai",
                "test-model",
                "draft:v1",
                "draft-output:v1",
                LocalDateTime.of(2026, 8, 12, 12, 0)
        ));
    }

    @Test
    void circuitOpenFailureNeverMarksProviderStartedOrCallsProvider() {
        AiProviderException circuitOpen = new AiProviderException(
                ErrorCode.AI_PROVIDER_UNAVAILABLE,
                new IllegalStateException("circuit open"));
        doThrow(circuitOpen).when(executionControl)
                .beforeProviderCall(AiFeature.DRAFT_GENERATION);

        assertThatThrownBy(() -> service.invoke(
                startCommand,
                request,
                response -> new AiProcessedResult<>(response.content(), null)
        )).isSameAs(circuitOpen);

        verify(usageService, never()).markProviderStarted(job);
        verify(gateway, never()).generate(org.mockito.ArgumentMatchers.any());
        verify(usageService).completeFailure(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.same(circuitOpen),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void providerStartIsMarkedAfterCircuitCheckAndImmediatelyBeforeGateway() {
        given(gateway.generate(org.mockito.ArgumentMatchers.any()))
                .willReturn(new AiProviderResponse("ok", null, "stop", 1));

        service.invoke(
                startCommand,
                request,
                response -> new AiProcessedResult<>(response.content(), 91L)
        );

        InOrder order = inOrder(executionControl, usageService, gateway);
        order.verify(executionControl).beforeProviderCall(AiFeature.DRAFT_GENERATION);
        order.verify(usageService).markProviderStarted(job);
        order.verify(gateway).generate(org.mockito.ArgumentMatchers.any());
    }
}
