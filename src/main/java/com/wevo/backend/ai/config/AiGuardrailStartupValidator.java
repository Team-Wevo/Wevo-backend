package com.wevo.backend.ai.config;

import com.wevo.backend.ai.domain.AiFeature;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/** 운영에서 누락된 quota·비용·가격 설정이 무제한 호출로 퇴행하지 않게 시작을 중단한다. */
@Component
@Profile("prod")
public class AiGuardrailStartupValidator implements InitializingBean {

    private final AiProperties aiProperties;
    private final AiGuardrailProperties guardrailProperties;
    private final AiPricingProperties pricingProperties;

    public AiGuardrailStartupValidator(
            AiProperties aiProperties,
            AiGuardrailProperties guardrailProperties,
            AiPricingProperties pricingProperties
    ) {
        this.aiProperties = aiProperties;
        this.guardrailProperties = guardrailProperties;
        this.pricingProperties = pricingProperties;
    }

    @Override
    public void afterPropertiesSet() {
        if ("none".equals(aiProperties.provider())) {
            return;
        }
        if (!guardrailProperties.isEnabled()) {
            throw new IllegalStateException("운영 AI provider에는 wevo.ai.guardrails.enabled=true가 필수입니다.");
        }
        boolean missingPricing = pricingProperties.version() == null
                || Arrays.stream(AiFeature.values())
                .map(feature -> aiProperties.optionsFor(feature).model())
                .anyMatch(model -> !pricingProperties.models().containsKey(model));
        if (missingPricing) {
            throw new IllegalStateException("운영 AI 모델의 pricing snapshot 설정은 필수입니다.");
        }
    }
}
