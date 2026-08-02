package com.wevo.backend.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AiPricingPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "wevo.ai.pricing.version=test-pricing",
                    "wevo.ai.pricing.models.[gpt-5.6-luna].input-per-million-tokens=0.20",
                    "wevo.ai.pricing.models.[gpt-5.6-luna].output-per-million-tokens=1.20",
                    "wevo.ai.pricing.models.[gpt-5.6-luna].cache-read-per-million-tokens=0.02",
                    "wevo.ai.pricing.models.[gpt-5.6-luna].cache-write-per-million-tokens=0.25"
            );

    @Test
    void preservesModelIdContainingDotWhenBindingPricingMap() {
        contextRunner.run(context -> {
            AiPricingProperties properties = context.getBean(AiPricingProperties.class);

            assertThat(properties.models()).containsKey("gpt-5.6-luna");
        });
    }

    @EnableConfigurationProperties(AiPricingProperties.class)
    static class TestConfiguration {
    }
}
