package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.OutputSchemaId;
import com.wevo.backend.ai.client.StructuredOutputDefinition;
import com.wevo.backend.section.dto.request.SectionDraftSaveRequest;
import org.springframework.stereotype.Component;

/**
 * AI 초안 본문과 참조 ID echo 배열의 구조화 출력 스키마.
 *
 * <p>참조 배열은 Provider 응답의 구조만 검증하는 비권위 데이터다. 의미 검증과 근거 저장은
 * 서버의 신뢰된 synthesis context를 기준으로 수행한다.
 */
@Component
public class DraftGenerationOutputDefinition {

    public static final OutputSchemaId SCHEMA_ID =
            new OutputSchemaId("draft-generation-output", 3);

    private static final String ID_ARRAY = """
            {
              "type": "array",
              "items": {"type": "integer", "minimum": 1}
            }
            """;

    private static final String JSON_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "additionalProperties": false,
              "required": [
                "content",
                "evidenceOpinionIds",
                "evidenceIssueIds",
                "evidenceAnswerIds",
                "unresolvedGapIssueIds"
              ],
              "properties": {
                "content": {
                  "type": "string",
                  "minLength": 1,
                  "maxLength": %d,
                  "pattern": "^[\\\\s\\\\S]*\\\\S[\\\\s\\\\S]*$"
                },
                "evidenceOpinionIds": %s,
                "evidenceIssueIds": %s,
                "evidenceAnswerIds": %s,
                "unresolvedGapIssueIds": %s
              }
            }
            """.formatted(
            SectionDraftSaveRequest.MAX_CONTENT_LENGTH,
            ID_ARRAY,
            ID_ARRAY,
            ID_ARRAY,
            ID_ARRAY
    );

    private final StructuredOutputDefinition<DraftGenerationOutput> definition;

    public DraftGenerationOutputDefinition(DraftGenerationOutputValidator validator) {
        definition = StructuredOutputDefinition.of(
                SCHEMA_ID,
                DraftGenerationOutput.class,
                JSON_SCHEMA,
                validator
        );
    }

    public StructuredOutputDefinition<DraftGenerationOutput> get() {
        return definition;
    }
}
