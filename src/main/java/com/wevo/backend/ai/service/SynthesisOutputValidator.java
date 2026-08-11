package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputSemanticFailureReason;
import com.wevo.backend.ai.client.StructuredOutputValidator;
import com.wevo.backend.ai.dto.model.IssueDetectionOutput;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * AI 의견 정리 출력의 <b>의미 검증</b>.
 *
 * <p>쟁점 의미 계약은 AI-06 {@link IssueDetectionOutputValidator}에만 두고 재사용한다.
 * 이 검증기는 합의점 근거와 전체 coverage를 추가 검증한다.
 */
@Component
public class SynthesisOutputValidator implements StructuredOutputValidator<SynthesisAiOutput> {

    static final int MAX_CONSENSUS_LENGTH = 2_000;
    static final int MAX_CONSENSUS_EVIDENCE = 20;

    private final IssueDetectionOutputValidator issueValidator;

    public SynthesisOutputValidator(IssueDetectionOutputValidator issueValidator) {
        this.issueValidator = issueValidator;
    }

    @Override
    public void validate(SynthesisAiOutput output, StructuredOutputValidationContext context) {
        if (output == null || context == null) {
            throw reject(StructuredOutputSemanticFailureReason.OUTPUT_REQUIRED, "output");
        }
        requireText(output.consensusSummary(), MAX_CONSENSUS_LENGTH, "consensusSummary");
        validateConsensusEvidence(output.consensusEvidenceOpinionIds(), context);
        validateExactCoverage(output.coveredOpinionIds(), context);
        issueValidator.validate(new IssueDetectionOutput(output.issues()), context);
    }

    private void validateConsensusEvidence(
            List<Long> evidenceOpinionIds,
            StructuredOutputValidationContext context
    ) {
        if (evidenceOpinionIds == null) {
            throw reject(StructuredOutputSemanticFailureReason.COLLECTION_REQUIRED,
                    "consensusEvidenceOpinionIds");
        }
        if (evidenceOpinionIds.isEmpty()) {
            throw reject(StructuredOutputSemanticFailureReason.COLLECTION_EMPTY,
                    "consensusEvidenceOpinionIds", null, 1, 0);
        }
        if (evidenceOpinionIds.size() > MAX_CONSENSUS_EVIDENCE) {
            throw reject(StructuredOutputSemanticFailureReason.COLLECTION_LIMIT_EXCEEDED,
                    "consensusEvidenceOpinionIds", null,
                    MAX_CONSENSUS_EVIDENCE, evidenceOpinionIds.size());
        }
        Set<Long> unique = new HashSet<>();
        for (Long opinionId : evidenceOpinionIds) {
            if (!unique.add(opinionId)) {
                throw reject(StructuredOutputSemanticFailureReason.REFERENCE_DUPLICATED,
                        "consensusEvidenceOpinionIds", opinionId);
            }
            if (opinionId == null || !context.allowedResourceIds().contains(opinionId)) {
                throw reject(StructuredOutputSemanticFailureReason.REFERENCE_NOT_ALLOWED,
                        "consensusEvidenceOpinionIds", opinionId);
            }
        }
    }

    private void validateExactCoverage(
            List<Long> coveredOpinionIds,
            StructuredOutputValidationContext context
    ) {
        if (coveredOpinionIds == null) {
            throw reject(StructuredOutputSemanticFailureReason.COLLECTION_REQUIRED,
                    "coveredOpinionIds");
        }
        Set<Long> covered = new HashSet<>();
        for (Long opinionId : coveredOpinionIds) {
            if (!covered.add(opinionId)) {
                throw reject(StructuredOutputSemanticFailureReason.COVERAGE_REFERENCE_DUPLICATED,
                        "coveredOpinionIds", opinionId);
            }
            if (opinionId == null || !context.allowedResourceIds().contains(opinionId)) {
                throw reject(StructuredOutputSemanticFailureReason.COVERAGE_REFERENCE_UNEXPECTED,
                        "coveredOpinionIds", opinionId,
                        context.allowedResourceIds().size(), coveredOpinionIds.size());
            }
        }
        if (!covered.equals(context.allowedResourceIds())) {
            Long missingId = context.allowedResourceIds().stream()
                    .filter(id -> !covered.contains(id))
                    .sorted()
                    .findFirst()
                    .orElse(null);
            throw reject(StructuredOutputSemanticFailureReason.COVERAGE_REFERENCE_MISSING,
                    "coveredOpinionIds", missingId,
                    context.allowedResourceIds().size(), coveredOpinionIds.size());
        }
    }

    private void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw reject(StructuredOutputSemanticFailureReason.TEXT_INVALID, field);
        }
    }

    private StructuredOutputSemanticException reject(
            StructuredOutputSemanticFailureReason reason,
            String field
    ) {
        return new StructuredOutputSemanticException(reason, field);
    }

    private StructuredOutputSemanticException reject(
            StructuredOutputSemanticFailureReason reason,
            String field,
            Long resourceId
    ) {
        return new StructuredOutputSemanticException(reason, field, resourceId);
    }

    private StructuredOutputSemanticException reject(
            StructuredOutputSemanticFailureReason reason,
            String field,
            Long resourceId,
            Integer expectedCount,
            Integer actualCount
    ) {
        return new StructuredOutputSemanticException(
                reason, field, resourceId, expectedCount, actualCount);
    }
}
