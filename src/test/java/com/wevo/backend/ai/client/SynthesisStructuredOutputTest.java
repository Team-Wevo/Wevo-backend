package com.wevo.backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.ai.service.IssueDetectionOutputValidator;
import com.wevo.backend.ai.service.SynthesisAiOutput;
import com.wevo.backend.ai.service.SynthesisOutputDefinition;
import com.wevo.backend.ai.service.SynthesisOutputValidator;
import org.junit.jupiter.api.Test;

class SynthesisStructuredOutputTest {

    private final StructuredOutputDefinition<SynthesisAiOutput> definition =
            new SynthesisOutputDefinition(
                    new SynthesisOutputValidator(new IssueDetectionOutputValidator())
            ).get();
    private final StrictStructuredOutputConverter<SynthesisAiOutput> converter =
            new StrictStructuredOutputConverter<>(definition);

    @Test
    void acceptsCompleteContract() {
        assertThat(converter.convert("""
                {
                  "consensusSummary":"대학생 팀을 우선한다.",
                  "consensusEvidenceOpinionIds":[1],
                  "coveredOpinionIds":[1,2],
                  "issues":[{
                    "type":"GAP",
                    "description":"시장 근거가 부족하다.",
                    "evidenceOpinionIds":[2],
                    "question":null,
                    "options":[]
                  }]
                }
                """).isSuccess()).isTrue();
    }

    @Test
    void rejectsMissingCoverageWrongIssueSchemaAndUnexpectedValues() {
        assertSchemaFailure("""
                {
                  "consensusSummary":"합의",
                  "consensusEvidenceOpinionIds":[1],
                  "issues":[]
                }
                """);
        assertSchemaFailure("""
                {
                  "consensusSummary":"합의",
                  "consensusEvidenceOpinionIds":[1],
                  "coveredOpinionIds":[1],
                  "issues":[{
                    "type":"GAP",
                    "description":"근거 부족",
                    "question":null,
                    "options":[]
                  }]
                }
                """);
        assertSchemaFailure("""
                {
                  "consensusSummary":"합의",
                  "consensusEvidenceOpinionIds":[1],
                  "coveredOpinionIds":[1],
                  "issues":[],
                  "status":"SUCCEEDED"
                }
                """);
    }

    private void assertSchemaFailure(String json) {
        assertThat(converter.convert(json).failure())
                .isEqualTo(StructuredConversionFailure.SCHEMA_VALIDATION);
    }
}
