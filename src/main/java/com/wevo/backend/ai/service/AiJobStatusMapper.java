package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiErrorType;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

/** 내부 AI 작업 상태와 저장된 안전한 실패 사유를 외부 polling 계약으로 변환한다. */
@Component
public class AiJobStatusMapper {

    public MappedStatus map(AiJob job) {
        AiRequestStatus status = AiRequestStatus.from(job.getStatus());
        if (status != AiRequestStatus.FAILED) {
            return new MappedStatus(status, null, null);
        }

        AiErrorType errorType = job.getFinalErrorType();
        ErrorCode errorCode = errorType == null
                ? ErrorCode.AI_PROVIDER_ERROR
                : errorType.toErrorCode();
        String message = job.getSafeErrorMessage() == null
                || job.getSafeErrorMessage().isBlank()
                ? errorCode.getMessage()
                : job.getSafeErrorMessage();
        return new MappedStatus(status, errorCode.getCode(), message);
    }

    public record MappedStatus(
            AiRequestStatus status,
            String failureCode,
            String failureMessage
    ) {
        public boolean failed() {
            return status == AiRequestStatus.FAILED;
        }
    }
}
