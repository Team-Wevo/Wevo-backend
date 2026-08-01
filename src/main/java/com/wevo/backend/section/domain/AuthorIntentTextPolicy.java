package com.wevo.backend.section.domain;

import java.util.regex.Pattern;

/** 작성자 의도 한 문장에 공통 적용하는 정규화·형식 정책. */
public final class AuthorIntentTextPolicy {

    public static final int MAX_LENGTH = 300;

    private static final Pattern LIST_PREFIX = Pattern.compile(
            "^\\s*(?:[-*•]|\\d+[.)])\\s+.*$");
    private static final Pattern INLINE_WHITESPACE = Pattern.compile("[ \\t\\f]+");

    private AuthorIntentTextPolicy() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return INLINE_WHITESPACE.matcher(value.strip()).replaceAll(" ");
    }

    public static boolean isValid(String value) {
        if (value == null || value.isBlank() || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return false;
        }
        String normalized = normalize(value);
        return normalized.length() <= MAX_LENGTH && !LIST_PREFIX.matcher(normalized).matches();
    }
}
