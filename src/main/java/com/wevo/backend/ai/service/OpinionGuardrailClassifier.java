package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredAiProviderRequest;
import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.context.OpinionGuardrailContext;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.dto.model.OpinionGuardrailVerdict;
import com.wevo.backend.ai.prompt.OpinionGuardrailPromptFactory;
import com.wevo.backend.project.domain.Project;
import com.wevo.backend.section.domain.ProjectSection;
import com.wevo.backend.user.domain.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/**
 * 의견 본문을 AI 로 동기 분류한다. AI provider 가 구성됐을 때만 빈이 생성된다
 * ({@code @ConditionalOnBean(AiInvocationService)}) — 미구성 환경(local 등)에서는 부재한다.
 *
 * <p>비동기 job 을 만들지 않고 {@code invokeStructured} 를 <b>인라인</b>으로 호출한다(제출을
 * 블록해야 하드 리젝이 성립). 사용량 로깅·quota guardrail·검증·교정 재시도는 {@code invokeStructured}
 * 가 그대로 수행하며, {@code AiJob} 없이도 동작한다(감사 로그는 job 없는 형태로 남는다).
 */
@Service
@ConditionalOnBean(AiInvocationService.class)
public class OpinionGuardrailClassifier {

    private final OpinionGuardrailPromptFactory promptFactory;
    private final AiInvocationService invocationService;
    private final AiInputSnapshotHasher snapshotHasher;
    private final AiProperties aiProperties;

    public OpinionGuardrailClassifier(
            OpinionGuardrailPromptFactory promptFactory,
            AiInvocationService invocationService,
            AiInputSnapshotHasher snapshotHasher,
            AiProperties aiProperties
    ) {
        this.promptFactory = promptFactory;
        this.invocationService = invocationService;
        this.snapshotHasher = snapshotHasher;
        this.aiProperties = aiProperties;
    }

    public OpinionGuardrailVerdict classify(
            OpinionGuardrailContext context,
            Project project,
            ProjectSection section,
            User requestedBy
    ) {
        AiProperties.ModelOptions options =
                aiProperties.optionsFor(AiFeature.OPINION_CONTENT_GUARDRAIL);
        String inputSnapshotHash = snapshotHasher.snapshot(context, options).inputSnapshotHash();
        StructuredAiProviderRequest<OpinionGuardrailVerdict> request =
                promptFactory.providerRequest(context);
        AiUsageStartCommand usage = new AiUsageStartCommand(
                project,
                section,
                requestedBy,
                AiFeature.OPINION_CONTENT_GUARDRAIL,
                OpinionGuardrailContract.PROMPT_VERSION,
                inputSnapshotHash);
        return invocationService.invokeStructured(
                usage,
                request,
                response -> new AiProcessedResult<>(response.result(), null)).value();
    }
}
