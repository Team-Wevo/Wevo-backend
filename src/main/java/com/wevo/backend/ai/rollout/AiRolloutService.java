package com.wevo.backend.ai.rollout;

import com.wevo.backend.ai.config.AiGuardrailProperties;
import com.wevo.backend.ai.config.AiPricingProperties;
import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.ai.config.AiRolloutProperties;
import com.wevo.backend.ai.domain.AiFeature;
import com.wevo.backend.ai.prompt.PromptRegistry;
import com.wevo.backend.ai.prompt.PromptTemplateId;
import com.wevo.backend.ai.service.AiJobIdempotencyInput;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/** 승인된 불변 조합만 결정적으로 선택하는 기능별 rollout router. */
@Service
public class AiRolloutService implements InitializingBean {

    private final AiRolloutProperties properties;
    private final AiProperties aiProperties;
    private final AiPricingProperties pricingProperties;
    private final AiGuardrailProperties guardrailProperties;
    private final PromptRegistry promptRegistry;

    public AiRolloutService(
            AiRolloutProperties properties,
            AiProperties aiProperties,
            AiPricingProperties pricingProperties,
            AiGuardrailProperties guardrailProperties,
            PromptRegistry promptRegistry
    ) {
        this.properties = properties;
        this.aiProperties = aiProperties;
        this.pricingProperties = pricingProperties;
        this.guardrailProperties = guardrailProperties;
        this.promptRegistry = promptRegistry;
    }

    @Override
    public void afterPropertiesSet() {
        if (!properties.isEnabled()) {
            return;
        }
        properties.routes().forEach((featureKey, route) -> {
            validateRoutedCombination(featureKey, route.baseline());
            if (route.candidate() != null && !route.candidate().isBlank()) {
                validateRoutedCombination(featureKey, route.candidate());
            }
        });
    }

    public AiRolloutSelection select(AiJobIdempotencyInput input) {
        if (!properties.isEnabled()) {
            return baseline(input);
        }
        AiRolloutProperties.Route route = properties.routes().get(input.feature().configKey());
        if (route == null) {
            return baseline(input);
        }
        String selectedId = route.baseline();
        if (route.candidate() != null && !route.candidate().isBlank()) {
            boolean synthetic = route.syntheticProjectIds().contains(input.projectId());
            boolean canary = bucket(input) < route.canaryPercent();
            if (synthetic || canary) {
                selectedId = route.candidate();
            }
        }
        AiRolloutProperties.Combination combination = properties.combinations().get(selectedId);
        requireCompatible(input, combination);
        return new AiRolloutSelection(
                selectedId,
                combination.modelId(),
                combination.promptVersion(),
                combination.schemaVersion(),
                combination.reasoningEffort(),
                combination.pricingVersion(),
                combination.policyVersion()
        );
    }

    private AiRolloutSelection baseline(AiJobIdempotencyInput input) {
        String reasoning = aiProperties.openai().reasoningEffort();
        return new AiRolloutSelection(
                "baseline",
                input.modelId(),
                input.promptVersion(),
                input.schemaVersion(),
                reasoning,
                pricingProperties.version() == null ? "unpriced" : pricingProperties.version(),
                guardrailProperties.isEnabled() ? guardrailProperties.policyVersion() : "guardrails-disabled"
        );
    }

    private void validateRoutedCombination(String featureKey, String combinationId) {
        AiRolloutProperties.Combination combination = properties.combinations().get(combinationId);
        if (combination == null || !combination.approved()) {
            throw new IllegalStateException("평가와 사람 리뷰를 통과하지 않은 AI rollout 조합입니다: " + combinationId);
        }
        if (pricingProperties.version() == null
                || !pricingProperties.version().equals(combination.pricingVersion())
                || !pricingProperties.models().containsKey(combination.modelId())) {
            throw new IllegalStateException("AI rollout 조합의 pricing snapshot을 적용할 수 없습니다: " + combinationId);
        }
        String currentPolicy = guardrailProperties.isEnabled()
                ? guardrailProperties.policyVersion() : "guardrails-disabled";
        if (!currentPolicy.equals(combination.policyVersion())) {
            throw new IllegalStateException("AI rollout 조합의 guardrail policy snapshot이 현재 설정과 다릅니다: "
                    + combinationId);
        }
        PromptTemplateId promptId = PromptTemplateId.parseTrackingValue(combination.promptVersion());
        if (!expectedPromptName(featureKey).equals(promptId.promptName())) {
            throw new IllegalStateException("AI rollout prompt가 기능과 일치하지 않습니다: " + combinationId);
        }
        promptRegistry.get(promptId);
    }

    private void requireCompatible(
            AiJobIdempotencyInput input,
            AiRolloutProperties.Combination combination
    ) {
        PromptTemplateId baselinePrompt = PromptTemplateId.parseTrackingValue(input.promptVersion());
        PromptTemplateId selectedPrompt = PromptTemplateId.parseTrackingValue(combination.promptVersion());
        if (!baselinePrompt.promptName().equals(selectedPrompt.promptName())) {
            throw new IllegalStateException("rollout prompt family가 현재 기능과 일치하지 않습니다.");
        }
        if (!input.schemaVersion().equals(combination.schemaVersion())) {
            throw new IllegalStateException("rollout schema version이 현재 서버 validator와 일치하지 않습니다.");
        }
    }

    private int bucket(AiJobIdempotencyInput input) {
        String stableKey = input.feature().name() + ":" + input.projectId() + ":"
                + (input.projectSectionId() == null ? "project" : input.projectSectionId());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(stableKey.getBytes(StandardCharsets.UTF_8));
            return Integer.parseInt(HexFormat.of().formatHex(digest, 0, 2), 16) % 100;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String expectedPromptName(String featureKey) {
        return Map.of(
                AiFeature.ISSUE_DETECTION.configKey(), "issue-detection",
                AiFeature.OPINION_SYNTHESIS.configKey(), "opinion-synthesis",
                AiFeature.DRAFT_GENERATION.configKey(), "draft-generation",
                AiFeature.DRAFT_REVIEW.configKey(), "draft-review",
                AiFeature.AUTHOR_INTENT_EXTRACTION.configKey(), "author-intent",
                AiFeature.REVIEW_INTENT_COMPARISON.configKey(), "review-intent-comparison",
                AiFeature.OPINION_CLUSTERING.configKey(), "opinion-clustering",
                AiFeature.PROJECT_FLOW_REVIEW.configKey(), "project-flow-review"
        ).get(featureKey);
    }
}
