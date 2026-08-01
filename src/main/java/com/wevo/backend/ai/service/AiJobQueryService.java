package com.wevo.backend.ai.service;

import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.domain.AiRequestFeature;
import com.wevo.backend.ai.dto.response.AiJobResponse;
import com.wevo.backend.ai.dto.response.AiJobResponse.FailureResponse;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.service.ProjectAccessGuard;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** request ID 기반 공통 AI 작업 상태 조회와 프로젝트 단위 존재 숨김을 담당한다. */
@Service
public class AiJobQueryService {

    private final AiJobRepository aiJobRepository;
    private final ProjectAccessGuard projectAccessGuard;
    private final AiJobStatusMapper statusMapper;

    public AiJobQueryService(
            AiJobRepository aiJobRepository,
            ProjectAccessGuard projectAccessGuard,
            AiJobStatusMapper statusMapper
    ) {
        this.aiJobRepository = aiJobRepository;
        this.projectAccessGuard = projectAccessGuard;
        this.statusMapper = statusMapper;
    }

    @Transactional(readOnly = true)
    public AiJobResponse getJob(UUID requestId, Long userId) {
        AiJob job = aiJobRepository.findByRequestId(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_JOB_NOT_FOUND));
        if (job.getFeature() == AiFeature.AUTHOR_INTENT_EXTRACTION
                || job.getFeature() == AiFeature.REVIEW_INTENT_COMPARISON) {
            ProjectAccessGuard.hidingNonMember(
                    ErrorCode.AI_JOB_NOT_FOUND,
                    () -> projectAccessGuard.requireOwner(job.getProject().getId(), userId));
        } else {
            ProjectAccessGuard.hidingNonMember(
                    ErrorCode.AI_JOB_NOT_FOUND,
                    () -> projectAccessGuard.requireParticipant(job.getProject().getId(), userId));
        }

        AiJobStatusMapper.MappedStatus mapped = statusMapper.map(job);
        return new AiJobResponse(
                job.getRequestId(),
                AiRequestFeature.from(job.getFeature()),
                mapped.status(),
                mapped.failed()
                        ? new FailureResponse(mapped.failureCode(), mapped.failureMessage())
                        : null
        );
    }
}
