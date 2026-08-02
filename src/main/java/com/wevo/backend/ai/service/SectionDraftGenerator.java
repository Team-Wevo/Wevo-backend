package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.context.DraftGenerationContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.domain.AiJob;
import com.wevo.backend.ai.prompt.DraftGenerationPromptFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/** 단일 초안 생성 Provider 호출과 사용량 감사를 수행한다. */
@Service
@ConditionalOnBean(AiInvocationService.class)
public class SectionDraftGenerator {

    private final DraftGenerationPromptFactory promptFactory;
    private final AiInvocationService invocationService;

    public SectionDraftGenerator(
            DraftGenerationPromptFactory promptFactory,
            AiInvocationService invocationService
    ) {
        this.promptFactory = promptFactory;
        this.invocationService = invocationService;
    }

    public DraftGenerationOutput generate(AiJob job, DraftGenerationContext context) {
        if (job == null || context == null) {
            throw new IllegalArgumentException("AI 작업과 초안 생성 context는 필수입니다.");
        }
        StructuredAiProviderRequest<DraftGenerationOutput> request =
                promptFactory.providerRequest(context, job.getPromptVersion());
        AiUsageStartCommand usage = new AiUsageStartCommand(
                job,
                job.getProject(),
                job.getProjectSection(),
                job.getRequestedBy(),
                AiFeature.DRAFT_GENERATION,
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
