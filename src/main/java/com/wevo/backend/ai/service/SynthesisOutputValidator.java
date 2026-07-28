package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.client.StructuredOutputSemanticException;
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
            throw reject();
        }
        requireText(output.consensusSummary(), MAX_CONSENSUS_LENGTH);
        validateConsensusEvidence(output.consensusEvidenceOpinionIds(), context);
        validateExactCoverage(output.coveredOpinionIds(), context);
        issueValidator.validate(new IssueDetectionOutput(output.issues()), context);
    }

    private void validateConsensusEvidence(
            List<Long> evidenceOpinionIds,
            StructuredOutputValidationContext context
    ) {
        if (evidenceOpinionIds == null
                || evidenceOpinionIds.isEmpty()
                || evidenceOpinionIds.size() > MAX_CONSENSUS_EVIDENCE) {
            throw reject();
        }
        Set<Long> unique = new HashSet<>();
        for (Long opinionId : evidenceOpinionIds) {
            if (!unique.add(opinionId)) {
                throw reject();
            }
            context.requireAllowedResourceId(opinionId);
        }
    }

    private void validateExactCoverage(
            List<Long> coveredOpinionIds,
            StructuredOutputValidationContext context
    ) {
        if (coveredOpinionIds == null) {
            throw reject();
        }
        Set<Long> covered = new HashSet<>(coveredOpinionIds);
        if (covered.size() != coveredOpinionIds.size()
                || !covered.equals(context.allowedResourceIds())) {
            throw reject();
        }
    }

    private void requireText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw reject();
        }
    }

    private StructuredOutputSemanticException reject() {
        return new StructuredOutputSemanticException();
    }
}
