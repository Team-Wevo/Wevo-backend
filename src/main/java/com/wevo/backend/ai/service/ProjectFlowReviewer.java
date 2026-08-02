package com.wevo.backend.ai.service;

import com.wevo.backend.ai.context.AiTokenBudgetEstimator;
import com.wevo.backend.ai.context.ProjectFlowReviewContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.dto.model.ProjectFlowReviewOutput;
import com.wevo.backend.ai.dto.model.ProjectFlowFindingOutput;
import com.wevo.backend.ai.prompt.ProjectFlowReviewPromptFactory;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/** 전체 입력을 임의 절단하지 않고 기능별 budget 안에서 한 번에 점검한다. */
@Service
@ConditionalOnBean(AiInvocationService.class)
public class ProjectFlowReviewer {
    private final AiTokenBudgetEstimator budgetEstimator;
    private final ProjectFlowReviewPromptFactory promptFactory;
    private final AiInvocationService invocationService;
    private final ProjectFlowReviewOutputValidator outputValidator;

    public ProjectFlowReviewer(AiTokenBudgetEstimator budgetEstimator,
                               ProjectFlowReviewPromptFactory promptFactory,
                               AiInvocationService invocationService,
                               ProjectFlowReviewOutputValidator outputValidator) {
        this.budgetEstimator = budgetEstimator;
        this.promptFactory = promptFactory;
        this.invocationService = invocationService;
        this.outputValidator = outputValidator;
    }

    public ProjectFlowReviewOutput review(AiJob job, ProjectFlowReviewContext context) {
        if (job == null || context == null) {
            throw new IllegalArgumentException("AI 작업과 flow review context는 필수입니다.");
        }
        int sectionCount = context.sections().size();
        if (sectionCount < ProjectFlowReviewContract.MIN_SECTION_COUNT
                || sectionCount > ProjectFlowReviewContract.MAX_SECTION_COUNT) {
            throw new IllegalStateException("전체 흐름 점검 section 수는 2개 이상 9개 이하여야 합니다.");
        }
        ProjectFlowReviewPromptContext full = ProjectFlowReviewPromptContext.full(context);
        if (budgetEstimator.estimate(AiFeature.PROJECT_FLOW_REVIEW,
                promptFactory.tokenBudgetInput(full, job.getPromptVersion())).withinBudget()) {
            return invoke(job, promptFactory.providerRequest(full, job.getPromptVersion()));
        }
        return pairwise(job, context);
    }

    private ProjectFlowReviewOutput pairwise(AiJob job, ProjectFlowReviewContext context) {
        Map<String, ProjectFlowFindingOutput> merged = new LinkedHashMap<>();
        for (int left = 0; left < context.sections().size() - 1; left++) {
            for (int right = left + 1; right < context.sections().size(); right++) {
                ProjectFlowReviewPromptContext pair = ProjectFlowReviewPromptContext.pair(
                        context, context.sections().get(left), context.sections().get(right));
                budgetEstimator.requireWithinBudget(AiFeature.PROJECT_FLOW_REVIEW,
                        promptFactory.tokenBudgetInput(pair, job.getPromptVersion()));
                for (ProjectFlowFindingOutput finding :
                        invoke(job, promptFactory.providerRequest(pair, job.getPromptVersion())).findings()) {
                    merged.putIfAbsent(identity(finding), finding);
                }
            }
        }
        ProjectFlowReviewOutput result = new ProjectFlowReviewOutput(new ArrayList<>(merged.values()));
        Map<Long, String> contents = context.sections().stream().collect(Collectors.toUnmodifiableMap(
                section -> section.sectionId(), section -> section.content()));
        outputValidator.validate(result,
                StructuredOutputValidationContext.forResourceContents(contents));
        return result;
    }

    private String identity(ProjectFlowFindingOutput finding) {
        List<String> references = finding.sections().stream()
                .map(reference -> reference.sectionId() + ":" + reference.targetExcerpt())
                .sorted().toList();
        return finding.type().name() + "|" + String.join("|", references);
    }

    private ProjectFlowReviewOutput invoke(
            AiJob job,
            com.wevo.backend.ai.client.StructuredAiProviderRequest<ProjectFlowReviewOutput> request
    ) {
        return invocationService.invokeStructured(
                new AiUsageStartCommand(job, job.getProject(), null, job.getRequestedBy(),
                        AiFeature.PROJECT_FLOW_REVIEW, job.getPromptVersion(),
                        job.getInputSnapshotHash()),
                request,
                response -> new AiProcessedResult<>(response.result(), null)
        ).value();
    }
}
