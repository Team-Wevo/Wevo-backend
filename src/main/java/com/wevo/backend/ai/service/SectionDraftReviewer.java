package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.context.DraftReviewContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.dto.model.DraftReviewOutput;
import com.wevo.backend.ai.prompt.DraftReviewPromptFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/** 단일 사전 검토 Provider 호출과 사용량 감사를 수행한다. */
@Service
@ConditionalOnBean(AiInvocationService.class)
public class SectionDraftReviewer {

    private final DraftReviewPromptFactory promptFactory;
    private final AiInvocationService invocationService;

    public SectionDraftReviewer(
            DraftReviewPromptFactory promptFactory,
            AiInvocationService invocationService
    ) {
        this.promptFactory = promptFactory;
        this.invocationService = invocationService;
    }

    public DraftReviewOutput review(AiJob job, DraftReviewContext context) {
        if (job == null || context == null) {
            throw new IllegalArgumentException("AI 작업과 사전 검토 context는 필수입니다.");
        }
        StructuredAiProviderRequest<DraftReviewOutput> request =
                promptFactory.providerRequest(context, job.getPromptVersion());
        AiUsageStartCommand usage = new AiUsageStartCommand(
                job,
                job.getProject(),
                job.getProjectSection(),
                job.getRequestedBy(),
                AiFeature.DRAFT_REVIEW,
                job.getPromptVersion(),
                job.getInputSnapshotHash()
        );
        return invocationService.invokeStructured(
                usage,
                request,
                response -> new AiProcessedResult<>(response.result(), null)
        ).value();
    }
}
