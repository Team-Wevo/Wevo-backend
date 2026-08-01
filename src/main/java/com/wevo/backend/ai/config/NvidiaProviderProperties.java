package com.wevo.backend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.Locale;
import java.util.Set;

@ConfigurationProperties(prefix = "wevo.ai.nvidia")
public record NvidiaProviderProperties(
        String apiKey,
        String baseUrl,
        Double temperature,
        String reasoningEffort
) {

    private static final Set<String> SUPPORTED_REASONING_EFFORTS = Set.of("none", "high");

    public NvidiaProviderProperties(Double temperature, String reasoningEffort) {
        this(null, null, temperature, reasoningEffort);
    }

    @ConstructorBinding
    public NvidiaProviderProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://integrate.api.nvidia.com"
                : baseUrl.strip();
        while (baseUrl.endsWith("/") && baseUrl.length() > 1) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        temperature = temperature == null ? 0.1d : temperature;
        if (temperature < 0.0d || temperature > 1.0d) {
            throw new IllegalArgumentException("wevo.ai.nvidia.temperature는 0 이상 1 이하여야 합니다.");
        }
        reasoningEffort = reasoningEffort == null || reasoningEffort.isBlank()
                ? "none"
                : reasoningEffort.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_REASONING_EFFORTS.contains(reasoningEffort)) {
            throw new IllegalArgumentException("wevo.ai.nvidia.reasoning-effort는 none 또는 high여야 합니다.");
        }
    }

    public String clientBaseUrl() {
        return baseUrl.endsWith("/v1") ? baseUrl : baseUrl + "/v1";
    }

    public void validateConnectionSettings() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("wevo.ai.nvidia.api-key 설정은 필수입니다.");
        }
        try {
            java.net.URI uri = new java.net.URI(baseUrl);
            boolean validScheme = "https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme());
            if (!uri.isAbsolute() || !validScheme || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw invalidBaseUrl();
            }
        } catch (java.net.URISyntaxException exception) {
            throw invalidBaseUrl();
        }
    }

    private IllegalArgumentException invalidBaseUrl() {
        return new IllegalArgumentException("wevo.ai.nvidia.base-url은 올바른 HTTP(S) URL이어야 합니다.");
    }
}
