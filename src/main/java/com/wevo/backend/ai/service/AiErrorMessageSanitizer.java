package com.wevo.backend.ai.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class AiErrorMessageSanitizer {

    private static final int MAX_LENGTH = 500;
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)authorization\\s*[:=]\\s*bearer\\s+[^\\s,;]+"
    );
    private static final Pattern ANTHROPIC_KEY = Pattern.compile("(?i)sk-ant-[a-z0-9_-]+");
    private static final Pattern NVIDIA_KEY = Pattern.compile("(?i)nvapi" + "-[a-z0-9_-]+");
    private static final Pattern OPENAI_KEY = Pattern.compile("(?i)sk-(?:proj-)?[a-z0-9_-]{8,}");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}"
    );
    private static final Pattern KEY_ASSIGNMENT = Pattern.compile(
            "(?i)(?:anthropic|nvidia)_api_key\\s*[:=]\\s*[^\\s,;]+"
    );

    public String sanitize(String message) {
        if (message == null) {
            return null;
        }
        String sanitized = AUTHORIZATION.matcher(message).replaceAll("Authorization: [REDACTED]");
        sanitized = ANTHROPIC_KEY.matcher(sanitized).replaceAll("[REDACTED]");
        sanitized = NVIDIA_KEY.matcher(sanitized).replaceAll("[REDACTED]");
        sanitized = OPENAI_KEY.matcher(sanitized).replaceAll("[REDACTED]");
        sanitized = KEY_ASSIGNMENT.matcher(sanitized).replaceAll("AI_PROVIDER_API_KEY=[REDACTED]");
        sanitized = EMAIL.matcher(sanitized).replaceAll("[REDACTED_EMAIL]");
        return sanitized.length() <= MAX_LENGTH ? sanitized : sanitized.substring(0, MAX_LENGTH);
    }
}
