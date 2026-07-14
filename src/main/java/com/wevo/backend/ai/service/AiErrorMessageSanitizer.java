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
    private static final Pattern KEY_ASSIGNMENT = Pattern.compile(
            "(?i)anthropic_api_key\\s*[:=]\\s*[^\\s,;]+"
    );

    public String sanitize(String message) {
        if (message == null) {
            return null;
        }
        String sanitized = AUTHORIZATION.matcher(message).replaceAll("Authorization: [REDACTED]");
        sanitized = ANTHROPIC_KEY.matcher(sanitized).replaceAll("[REDACTED]");
        sanitized = KEY_ASSIGNMENT.matcher(sanitized).replaceAll("ANTHROPIC_API_KEY=[REDACTED]");
        return sanitized.length() <= MAX_LENGTH ? sanitized : sanitized.substring(0, MAX_LENGTH);
    }
}
