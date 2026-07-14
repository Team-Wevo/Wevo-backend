package com.wevo.backend.ai.client;

import java.util.regex.Pattern;

public record OutputSchemaId(String name, int version) {

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");

    public OutputSchemaId {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("schema name은 kebab-case여야 합니다.");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("schema version은 1 이상이어야 합니다.");
        }
    }

    public String trackingValue() {
        return name + ":v" + version;
    }
}
