package com.wevo.backend.ai.context;

import com.wevo.backend.ai.config.AiProperties;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * AI 입력을 결정적인 canonical JSON으로 직렬화하고 SHA-256 hash를 만든다.
 *
 * <p>규칙은 UTF-8, 알파벳순 object field/map key, null과 빈 목록 보존, compact JSON,
 * 문자열 줄바꿈 LF 정규화다. 기능별 목록 순서는 context assembler가 도메인 key로 정렬한다.</p>
 */
@Component
public class AiInputSnapshotHasher {

    public static final String CANONICAL_VERSION = "ai-context-canonical-v1";

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    public AiInputSnapshot snapshot(
            AiFeatureContext context,
            AiProperties.ModelOptions options
    ) {
        if (context == null || options == null) {
            throw new IllegalArgumentException("AI context와 model options는 필수입니다.");
        }
        SnapshotEnvelope envelope = new SnapshotEnvelope(
                CANONICAL_VERSION,
                context.feature(),
                options.maxInputTokens(),
                options.singleInputOverflowPolicy(),
                options.tokenEstimationPolicy(),
                context
        );
        return canonicalSnapshot(envelope);
    }

    public AiInputSnapshot canonicalSnapshot(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("canonical 직렬화 대상은 필수입니다.");
        }
        try {
            JsonNode tree = normalizeStrings(jsonMapper.valueToTree(value));
            byte[] bytes = jsonMapper.writeValueAsBytes(tree);
            return new AiInputSnapshot(bytes, sha256Hex(bytes));
        } catch (RuntimeException exception) {
            throw new AiContextSerializationException(exception);
        }
    }

    public String hashCanonical(Object value) {
        return canonicalSnapshot(value).inputSnapshotHash();
    }

    private JsonNode normalizeStrings(JsonNode node) {
        if (node.isString()) {
            return JsonNodeFactory.instance.stringNode(normalizeLineEndings(node.stringValue()));
        }
        if (node.isObject()) {
            node.properties().forEach(entry ->
                    node.asObject().set(entry.getKey(), normalizeStrings(entry.getValue())));
            return node;
        }
        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                node.asArray().set(index, normalizeStrings(node.get(index)));
            }
        }
        return node;
    }

    private String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private record SnapshotEnvelope(
            String canonicalVersion,
            com.wevo.backend.ai.domain.AiFeature feature,
            int maxInputTokens,
            String singleInputOverflowPolicy,
            String tokenEstimationPolicy,
            AiFeatureContext context
    ) {
    }
}
