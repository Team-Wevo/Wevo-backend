package com.wevo.backend.ai.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class AiJobIdempotencyKeyGenerator {

    private static final String CANONICAL_VERSION = "wevo-ai-job-idempotency:v1";

    public String generate(AiJobIdempotencyInput input) {
        return generate(input, true);
    }

    public String generateLegacy(AiJobIdempotencyInput input) {
        return generate(input, false);
    }

    private String generate(AiJobIdempotencyInput input, boolean includeReasoning) {
        if (input == null) {
            throw new IllegalArgumentException("idempotency input은 필수입니다.");
        }

        MessageDigest digest = sha256();
        update(digest, CANONICAL_VERSION);
        update(digest, input.feature().name());
        update(digest, input.projectId().toString());
        update(digest, input.projectSectionId() == null ? "<null>" : input.projectSectionId().toString());
        update(digest, input.inputSnapshotHash());
        update(digest, input.sourceVersion());
        update(digest, input.promptVersion());
        update(digest, input.schemaVersion());
        update(digest, input.modelId());
        update(digest, input.maxOutputTokens().toString());
        if (includeReasoning) {
            update(digest, input.reasoningEffort());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm을 사용할 수 없습니다.", exception);
        }
    }
}
