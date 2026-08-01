package com.wevo.backend.ai.config;

import com.wevo.backend.ai.domain.AiFeature;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Wevo AI 기능에서 사용하는 모델 실행 정책.
 *
 * <p>Provider 선택·연결과 기능별 모델, 제한 시간, 입출력 token budget을 함께 관리한다.</p>
 *
 * <p>{@code maxInputTokens}는 system/user prompt, JSON schema와 context payload를 모두 포함하는
 * Provider 입력 상한이다. {@code safetyMarginTokens}는 여기에 포함되지 않는 context window의
 * 미할당 여유분이며, 항상
 * {@code maxInputTokens + maxOutputTokens + safetyMarginTokens <= modelContextLimit}이어야 한다.</p>
 */
@Validated
@ConfigurationProperties(prefix = "wevo.ai")
public record AiProperties(
        String provider,
        ModelOptions defaultOptions,
        Map<String, FeatureOptions> features,
        StructuredOutputOptions structuredOutput,
        OpenAiOptions openai
) {

    public AiProperties(
            String provider,
            ModelOptions defaultOptions,
            Map<String, FeatureOptions> features,
            StructuredOutputOptions structuredOutput
    ) {
        this(provider, defaultOptions, features, structuredOutput, null);
    }

    @ConstructorBinding
    public AiProperties {
        provider = provider == null || provider.isBlank() ? "none" : provider.toLowerCase(java.util.Locale.ROOT);
        if (!provider.matches("[a-z][a-z0-9-]{0,29}")) {
            throw new IllegalArgumentException("wevo.ai.provider는 소문자 provider 식별자여야 합니다.");
        }
        if (!provider.equals("none") && !provider.equals("nvidia") && !provider.equals("openai")) {
            throw new IllegalArgumentException("지원하지 않는 AI provider입니다: " + provider);
        }
        if (defaultOptions == null) {
            throw new IllegalArgumentException("wevo.ai.default-options 설정은 필수입니다.");
        }
        defaultOptions.validate("wevo.ai.default-options");
        features = features == null ? Map.of() : Map.copyOf(features);
        Set<String> supportedFeatureKeys = new HashSet<>();
        for (AiFeature feature : AiFeature.values()) {
            supportedFeatureKeys.add(feature.configKey());
        }
        if (!supportedFeatureKeys.containsAll(features.keySet())) {
            throw new IllegalArgumentException("wevo.ai.features에 지원하지 않는 기능 key가 있습니다.");
        }
        openai = openai == null ? OpenAiOptions.defaults() : openai;
        if (provider.equals("openai")) {
            openai.validate();
        }
        ModelOptions providerDefaults = providerDefaults(defaultOptions, provider, openai);
        for (AiFeature feature : AiFeature.values()) {
            resolveOptions(providerDefaults, features, feature)
                    .validate("wevo.ai.features." + feature.configKey());
        }
        structuredOutput = structuredOutput == null ? new StructuredOutputOptions(2) : structuredOutput;
    }

    public ModelOptions optionsFor(AiFeature feature) {
        if (feature == null) {
            throw new IllegalArgumentException("AI feature는 필수입니다.");
        }
        return resolveOptions(providerDefaultOptions(), features, feature);
    }

    public ModelOptions providerDefaultOptions() {
        return providerDefaults(defaultOptions, provider, openai);
    }

    private static ModelOptions providerDefaults(
            ModelOptions defaults,
            String provider,
            OpenAiOptions openai
    ) {
        if (!"openai".equals(provider)) {
            return defaults;
        }
        ModelOptions resolved = new ModelOptions(
                openai.model(),
                openai.timeout(),
                defaults.maxInputTokens(),
                openai.maxOutputTokens(),
                openai.modelContextLimit(),
                defaults.safetyMarginTokens(),
                defaults.tokenEstimationPolicy(),
                defaults.singleInputOverflowPolicy(),
                defaults.maxRetries(),
                defaults.initialBackoff(),
                defaults.maxBackoff()
        );
        resolved.validate("wevo.ai.openai");
        return resolved;
    }

    private static ModelOptions resolveOptions(
            ModelOptions defaults,
            Map<String, FeatureOptions> configuredFeatures,
            AiFeature feature
    ) {
        String featureKey = feature.configKey();
        FeatureOptions featureOptions = configuredFeatures.get(featureKey);
        if (featureOptions == null) {
            return defaults;
        }

        ModelOptions resolved = new ModelOptions(
                StringUtils.hasText(featureOptions.model())
                        ? featureOptions.model()
                        : defaults.model(),
                featureOptions.timeout() != null
                        ? featureOptions.timeout()
                        : defaults.timeout(),
                featureOptions.maxInputTokens() != null
                        ? featureOptions.maxInputTokens()
                        : defaults.maxInputTokens(),
                featureOptions.maxOutputTokens() != null
                        ? featureOptions.maxOutputTokens()
                        : defaults.maxOutputTokens(),
                featureOptions.modelContextLimit() != null
                        ? featureOptions.modelContextLimit()
                        : defaults.modelContextLimit(),
                featureOptions.safetyMarginTokens() != null
                        ? featureOptions.safetyMarginTokens()
                        : defaults.safetyMarginTokens(),
                StringUtils.hasText(featureOptions.tokenEstimationPolicy())
                        ? featureOptions.tokenEstimationPolicy()
                        : defaults.tokenEstimationPolicy(),
                StringUtils.hasText(featureOptions.singleInputOverflowPolicy())
                        ? featureOptions.singleInputOverflowPolicy()
                        : defaults.singleInputOverflowPolicy(),
                featureOptions.maxRetries() != null
                        ? featureOptions.maxRetries()
                        : defaults.maxRetries(),
                featureOptions.initialBackoff() != null
                        ? featureOptions.initialBackoff()
                        : defaults.initialBackoff(),
                featureOptions.maxBackoff() != null
                        ? featureOptions.maxBackoff()
                        : defaults.maxBackoff()
        );
        resolved.validate("wevo.ai.features." + featureKey);
        return resolved;
    }

    public record ModelOptions(
            String model,
            Duration timeout,
            Integer maxInputTokens,
            Integer maxOutputTokens,
            Integer modelContextLimit,
            Integer safetyMarginTokens,
            String tokenEstimationPolicy,
            String singleInputOverflowPolicy,
            Integer maxRetries,
            Duration initialBackoff,
            Duration maxBackoff
    ) {

        public static final String CONSERVATIVE_CHAR_V1 = "conservative-char-v1";
        public static final String REJECT_OVERSIZED_INPUT_V1 = "reject-oversized-input-v1";

        private void validate(String path) {
            if (!StringUtils.hasText(model)) {
                throw new IllegalArgumentException(path + ".model 설정은 필수입니다.");
            }
            if (model.length() > 100) {
                throw new IllegalArgumentException(path + ".model은 100자 이하여야 합니다.");
            }
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException(path + ".timeout은 0보다 커야 합니다.");
            }
            if (maxOutputTokens == null || maxOutputTokens <= 0 || maxOutputTokens > 128_000) {
                throw new IllegalArgumentException(path + ".max-output-tokens는 1 이상 128000 이하여야 합니다.");
            }
            if (maxInputTokens == null || maxInputTokens <= 0) {
                throw new IllegalArgumentException(path + ".max-input-tokens는 0보다 커야 합니다.");
            }
            if (modelContextLimit == null || modelContextLimit <= 0 || modelContextLimit > 2_000_000) {
                throw new IllegalArgumentException(
                        path + ".model-context-limit은 1 이상 2000000 이하여야 합니다.");
            }
            if (safetyMarginTokens == null || safetyMarginTokens <= 0) {
                throw new IllegalArgumentException(path + ".safety-margin-tokens는 0보다 커야 합니다.");
            }
            long reserved = (long) maxInputTokens + maxOutputTokens + safetyMarginTokens;
            if (reserved > modelContextLimit) {
                throw new IllegalArgumentException(
                        path + "의 input + output + safety margin이 model context limit을 초과합니다.");
            }
            if (!CONSERVATIVE_CHAR_V1.equals(tokenEstimationPolicy)) {
                throw new IllegalArgumentException(
                        path + ".token-estimation-policy는 지원되는 version이어야 합니다.");
            }
            if (!REJECT_OVERSIZED_INPUT_V1.equals(singleInputOverflowPolicy)) {
                throw new IllegalArgumentException(
                        path + ".single-input-overflow-policy는 지원되는 version이어야 합니다.");
            }
            if (maxRetries == null || maxRetries < 0) {
                throw new IllegalArgumentException(path + ".max-retries는 0 이상이어야 합니다.");
            }
            if (initialBackoff == null || initialBackoff.isNegative()) {
                throw new IllegalArgumentException(path + ".initial-backoff은 0 이상이어야 합니다.");
            }
            if (maxBackoff == null || maxBackoff.isNegative() || maxBackoff.compareTo(initialBackoff) < 0) {
                throw new IllegalArgumentException(path + ".max-backoff은 initial-backoff 이상이어야 합니다.");
            }
        }
    }

    public record FeatureOptions(
            String model,
            Duration timeout,
            Integer maxInputTokens,
            Integer maxOutputTokens,
            Integer modelContextLimit,
            Integer safetyMarginTokens,
            String tokenEstimationPolicy,
            String singleInputOverflowPolicy,
            Integer maxRetries,
            Duration initialBackoff,
            Duration maxBackoff
    ) {
    }

    public record StructuredOutputOptions(Integer maxCorrectionRetries) {

        public StructuredOutputOptions {
            maxCorrectionRetries = maxCorrectionRetries == null ? 2 : maxCorrectionRetries;
            if (maxCorrectionRetries < 0 || maxCorrectionRetries > 5) {
                throw new IllegalArgumentException(
                        "wevo.ai.structured-output.max-correction-retries는 0 이상 5 이하여야 합니다."
                );
            }
        }
    }

    public record OpenAiOptions(
            String apiKey,
            String baseUrl,
            String model,
            Duration timeout,
            Integer maxOutputTokens,
            String reasoningEffort,
            Integer modelContextLimit
    ) {

        private static final Set<String> SUPPORTED_REASONING_EFFORTS =
                Set.of("none", "low", "medium", "high", "xhigh", "max");

        public OpenAiOptions {
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com" : baseUrl.strip();
            while (baseUrl.endsWith("/") && baseUrl.length() > 1) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            model = model == null || model.isBlank() ? "gpt-5.6-luna" : model.strip();
            timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
            maxOutputTokens = maxOutputTokens == null ? 4096 : maxOutputTokens;
            reasoningEffort = reasoningEffort == null || reasoningEffort.isBlank()
                    ? "medium"
                    : reasoningEffort.toLowerCase(Locale.ROOT);
            modelContextLimit = modelContextLimit == null ? 1_050_000 : modelContextLimit;
        }

        private static OpenAiOptions defaults() {
            return new OpenAiOptions(null, null, null, null, null, null, null);
        }

        public String clientBaseUrl() {
            return baseUrl.endsWith("/v1") ? baseUrl : baseUrl + "/v1";
        }

        private void validate() {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalArgumentException("wevo.ai.openai.api-key 설정은 필수입니다.");
            }
            if (!SUPPORTED_REASONING_EFFORTS.contains(reasoningEffort)) {
                throw new IllegalArgumentException("wevo.ai.openai.reasoning-effort가 지원되지 않습니다.");
            }
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("wevo.ai.openai.timeout은 0보다 커야 합니다.");
            }
            if (maxOutputTokens <= 0 || maxOutputTokens > 128_000) {
                throw new IllegalArgumentException(
                        "wevo.ai.openai.max-output-tokens는 1 이상 128000 이하여야 합니다.");
            }
            validateBaseUrl();
        }

        private void validateBaseUrl() {
            try {
                URI uri = new URI(baseUrl);
                boolean validScheme = "https".equalsIgnoreCase(uri.getScheme())
                        || "http".equalsIgnoreCase(uri.getScheme());
                if (!uri.isAbsolute() || !validScheme || uri.getHost() == null
                        || uri.getUserInfo() != null || uri.getQuery() != null
                        || uri.getFragment() != null) {
                    throw invalidBaseUrl();
                }
            } catch (URISyntaxException exception) {
                throw invalidBaseUrl();
            }
        }

        private IllegalArgumentException invalidBaseUrl() {
            return new IllegalArgumentException("wevo.ai.openai.base-url은 올바른 HTTP(S) URL이어야 합니다.");
        }
    }
}
