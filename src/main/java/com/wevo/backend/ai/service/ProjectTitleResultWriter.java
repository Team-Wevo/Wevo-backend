package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.context.ProjectTitleContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.dto.model.ProjectTitleSuggestionOutput;
import com.wevo.backend.ai.repository.AiJobRepository;
import com.wevo.backend.global.exception.BusinessException;
import com.wevo.backend.global.exception.ErrorCode;
import com.wevo.backend.project.service.ProjectService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 생성된 제목을 project 도메인 공개 창구로 되채운다. 여전히 서버 기본값일 때만 반영된다
 * (사용자가 그 사이 직접 바꿨으면 존중 — {@link ProjectService#applyAiGeneratedTitle}).
 */
@Service
public class ProjectTitleResultWriter {

    private final AiJobRepository jobRepository;
    private final ProjectService projectService;
    private final ProjectTitleOutputValidator outputValidator;

    public ProjectTitleResultWriter(
            AiJobRepository jobRepository,
            ProjectService projectService,
            ProjectTitleOutputValidator outputValidator
    ) {
        this.jobRepository = jobRepository;
        this.projectService = projectService;
        this.outputValidator = outputValidator;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long persist(
            UUID requestId,
            ProjectTitleContext context,
            ProjectTitleSuggestionOutput output
    ) {
        AiJob job = jobRepository.findByRequestId(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_JOB_NOT_FOUND));
        if (job.getFeature() != AiFeature.PROJECT_TITLE_SUGGESTION
                || !job.getProject().getId().equals(context.projectId())) {
            throw new IllegalArgumentException("프로젝트 제목 저장 입력이 유효하지 않습니다.");
        }
        outputValidator.validate(output, StructuredOutputValidationContext.empty());
        projectService.applyAiGeneratedTitle(context.projectId(), output.title().strip());
        // 제목 생성은 별도 결과 엔티티가 없다 — 사용량 로그 연결용 resultId 로 프로젝트 ID 를 쓴다.
        return context.projectId();
    }
}
