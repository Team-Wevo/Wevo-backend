package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.wevo.backend.ai.client.StructuredOutputExecutionContext;
import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputSemanticFailureReason;
import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.domain.AiCostSnapshot;
import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.exception.AiProviderException;
import com.wevo.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AiUsageServiceTest {

    @Test
    void semanticFailureLogsOnlySafeStructuredDiagnostics() {
        AiUsagePersistenceService persistenceService = mock(AiUsagePersistenceService.class);
        AiCostCalculator costCalculator = mock(AiCostCalculator.class);
        AiErrorClassifier classifier = mock(AiErrorClassifier.class);
        AiErrorMessageSanitizer sanitizer = mock(AiErrorMessageSanitizer.class);
        AiGuardrailService guardrailService = mock(AiGuardrailService.class);
        AiUsageService service = new AiUsageService(
                persistenceService,
                mock(AiProperties.class),
                costCalculator,
                classifier,
                sanitizer,
                guardrailService,
                Clock.fixed(Instant.parse("2026-08-11T12:00:00Z"), ZoneId.of("Asia/Seoul"))
        );
        AiJob job = mock(AiJob.class);
        UUID jobRequestId = UUID.randomUUID();
        given(job.getRequestId()).willReturn(jobRequestId);
        given(costCalculator.calculate(null, "gpt-5.6-luna"))
                .willReturn(AiCostSnapshot.unpriced("test"));

        StructuredOutputSemanticException semanticFailure =
                new StructuredOutputSemanticException(
                        StructuredOutputSemanticFailureReason.REFERENCE_NOT_ALLOWED,
                        "issues[0].evidenceOpinionIds",
                        999L
                ).withExecutionContext(new StructuredOutputExecutionContext("PARTIAL", 2));
        AiProviderException providerFailure = new AiProviderException(
                ErrorCode.AI_STRUCTURED_OUTPUT_SEMANTIC_VALIDATION_FAILED,
                semanticFailure
        );
        given(classifier.classify(providerFailure))
                .willReturn(AiErrorType.SEMANTIC_VALIDATION_FAILED);
        given(classifier.safeMessage(providerFailure))
                .willReturn(ErrorCode.AI_STRUCTURED_OUTPUT_SEMANTIC_VALIDATION_FAILED.getMessage());
        given(sanitizer.sanitize(any())).willReturn("AI 응답의 참조값이 유효하지 않습니다.");

        Logger logger = (Logger) LoggerFactory.getLogger(AiUsageService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.completeFailure(
                    new AiUsageHandle(
                            1L,
                            UUID.randomUUID(),
                            job,
                            AiFeature.OPINION_SYNTHESIS,
                            "openai",
                            "gpt-5.6-luna",
                            "opinion-synthesis:v2",
                            "opinion-synthesis-output:v2",
                            LocalDateTime.of(2026, 8, 11, 20, 59)
                    ),
                    providerFailure,
                    null,
                    1
            );
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        ILoggingEvent event = appender.list.getLast();
        Map<String, Object> values = event.getKeyValuePairs().stream()
                .filter(pair -> pair.value != null)
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
        assertThat(values)
                .containsEntry("aiJobRequestId", jobRequestId)
                .containsEntry("semanticFailureReason",
                        StructuredOutputSemanticFailureReason.REFERENCE_NOT_ALLOWED)
                .containsEntry("semanticFailureField", "issues[0].evidenceOpinionIds")
                .containsEntry("offendingResourceId", 999L)
                .containsEntry("structuredStage", "PARTIAL")
                .containsEntry("chunkIndex", 2);
        assertThat(event.getFormattedMessage())
                .isEqualTo("AI invocation completed")
                .doesNotContain("사용자 의견")
                .doesNotContain("completion");
    }
}
