package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiJobStatus;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

class AiJobStatusMapperTest {

    private final AiJobStatusMapper mapper = new AiJobStatusMapper();

    @ParameterizedTest
    @EnumSource(value = AiJobStatus.class, names = {"QUEUED", "RUNNING"})
    void queuedAndRunningMapToRequested(AiJobStatus status) {
        AiJobStatusMapper.MappedStatus mapped =
                mapper.map(job(status, null, null));

        assertThat(mapped.status()).isEqualTo(AiRequestStatus.REQUESTED);
        assertThat(mapped.failed()).isFalse();
        assertThat(mapped.failureCode()).isNull();
    }

    @Test
    void succeededMapsWithoutFailure() {
        AiJobStatusMapper.MappedStatus mapped =
                mapper.map(job(AiJobStatus.SUCCEEDED, null, null));

        assertThat(mapped.status()).isEqualTo(AiRequestStatus.SUCCEEDED);
        assertThat(mapped.failed()).isFalse();
    }

    @Test
    void structuredSchemaFailureUsesCanonicalCodeAndSafeMessage() {
        AiJobStatusMapper.MappedStatus mapped = mapper.map(job(
                AiJobStatus.FAILED,
                AiErrorType.SCHEMA_VALIDATION_FAILED,
                "AI 응답이 출력 형식을 충족하지 않습니다."
        ));

        assertThat(mapped.status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(mapped.failureCode()).isEqualTo("AI018");
        assertThat(mapped.failureMessage())
                .isEqualTo("AI 응답이 출력 형식을 충족하지 않습니다.");
    }

    @Test
    void staleMapsToAi024AndMissingFailureFallsBackToAi999() {
        assertThat(mapper.map(job(
                AiJobStatus.STALE,
                AiErrorType.STALE_INPUT,
                "입력이 변경되어 결과를 폐기했습니다."
        )).failureCode()).isEqualTo("AI024");

        AiJobStatusMapper.MappedStatus cancelled =
                mapper.map(job(AiJobStatus.CANCELLED, null, null));
        assertThat(cancelled.status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(cancelled.failureCode()).isEqualTo("AI999");
        assertThat(cancelled.failureMessage())
                .isEqualTo(ErrorCode.AI_PROVIDER_ERROR.getMessage());
    }

    private AiJob job(
            AiJobStatus status,
            AiErrorType errorType,
            String safeErrorMessage
    ) {
        AiJob job = Mockito.mock(AiJob.class);
        given(job.getStatus()).willReturn(status);
        given(job.getFinalErrorType()).willReturn(errorType);
        given(job.getSafeErrorMessage()).willReturn(safeErrorMessage);
        return job;
    }
}
