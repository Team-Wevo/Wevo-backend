package com.wevo.backend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;

/**
 * Wevo AI 기능에서 사용하는 모델 실행 정책.
 *
 * <p>Anthropic 연결 자체의 설정은 {@code spring.ai.anthropic}이 담당하고,
 * 이 설정은 기능별 모델, 제한 시간, 출력 토큰 한도를 선택하는 데 사용한다.</p>
 */
@Validated
@ConfigurationProperties(prefix = "wevo.ai")
public record AiProperties(
        ModelOptions defaultOptions,
        Map<String, FeatureOptions> features
) {

    public AiProperties {
        if (defaultOptions == null) {
            throw new IllegalArgumentException("wevo.ai.default-options 설정은 필수입니다.");
        }
        defaultOptions.validate("wevo.ai.default-options");
        features = features == null ? Map.of() : Map.copyOf(features);
    }

    public ModelOptions optionsFor(String featureName) {
        FeatureOptions featureOptions = features.get(featureName);
        if (featureOptions == null) {
            return defaultOptions;
        }

        ModelOptions resolved = new ModelOptions(
                StringUtils.hasText(featureOptions.model())
                        ? featureOptions.model()
                        : defaultOptions.model(),
                featureOptions.timeout() != null
                        ? featureOptions.timeout()
                        : defaultOptions.timeout(),
                featureOptions.maxOutputTokens() != null
                        ? featureOptions.maxOutputTokens()
                        : defaultOptions.maxOutputTokens()
        );
        resolved.validate("wevo.ai.features." + featureName);
        return resolved;
    }

    public record ModelOptions(String model, Duration timeout, Integer maxOutputTokens) {

        private void validate(String path) {
            if (!StringUtils.hasText(model)) {
                throw new IllegalArgumentException(path + ".model 설정은 필수입니다.");
            }
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException(path + ".timeout은 0보다 커야 합니다.");
            }
            if (maxOutputTokens == null || maxOutputTokens <= 0) {
                throw new IllegalArgumentException(path + ".max-output-tokens는 0보다 커야 합니다.");
            }
        }
    }

    public record FeatureOptions(String model, Duration timeout, Integer maxOutputTokens) {
    }
}
