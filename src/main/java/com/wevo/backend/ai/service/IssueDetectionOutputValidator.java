package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputSemanticFailureReason;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.client.StructuredOutputValidator;
import com.wevo.backend.ai.context.AiOpinionContext;
import com.wevo.backend.ai.context.IssueDetectionContext;
import com.wevo.backend.ai.dto.model.IssueDetectionIssueOutput;
import com.wevo.backend.ai.dto.model.IssueDetectionOutput;
import com.wevo.backend.issue.domain.IssueType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 쟁점 감지 출력의 서버 소유 의미 계약.
 *
 * <p>검증 실패에는 모델 출력 원문이나 의견 내용을 포함하지 않는다.</p>
 */
@Component
public class IssueDetectionOutputValidator implements StructuredOutputValidator<IssueDetectionOutput> {

    public static final int MAX_ISSUES = 4;
    public static final int MAX_CONFLICTS = 3;
    public static final int MAX_GAPS = 2;
    public static final int MAX_DESCRIPTION_LENGTH = 1_000;
    public static final int MAX_QUESTION_LENGTH = 500;
    public static final int MIN_CONFLICT_OPTIONS = 2;
    public static final int MAX_CONFLICT_OPTIONS = 4;
    public static final int MAX_OPTION_LENGTH = 200;
    public static final int MAX_EVIDENCE_PER_ISSUE = 20;

    @Override
    public void validate(
            IssueDetectionOutput output,
            StructuredOutputValidationContext validationContext
    ) {
        if (output == null || output.issues() == null || validationContext == null) {
            fail(StructuredOutputSemanticFailureReason.OUTPUT_REQUIRED, "issues");
        }
        List<IssueDetectionIssueOutput> issues = output.issues();
        if (issues.size() > MAX_ISSUES) {
            fail(StructuredOutputSemanticFailureReason.ISSUE_LIMIT_EXCEEDED,
                    "issues", null, MAX_ISSUES, issues.size());
        }

        int conflicts = 0;
        int gaps = 0;
        for (int index = 0; index < issues.size(); index++) {
            IssueDetectionIssueOutput issue = issues.get(index);
            validateIssue(issue, validationContext, index);
            if (issue.type() == IssueType.CONFLICT) {
                conflicts++;
            } else if (issue.type() == IssueType.GAP) {
                gaps++;
            }
        }
        if (conflicts > MAX_CONFLICTS || gaps > MAX_GAPS) {
            fail(StructuredOutputSemanticFailureReason.ISSUE_TYPE_LIMIT_EXCEEDED,
                    "issues", null,
                    conflicts > MAX_CONFLICTS ? MAX_CONFLICTS : MAX_GAPS,
                    conflicts > MAX_CONFLICTS ? conflicts : gaps);
        }
    }

    public void validate(IssueDetectionOutput output, IssueDetectionContext context) {
        if (context == null || context.opinions() == null) {
            fail(StructuredOutputSemanticFailureReason.OUTPUT_REQUIRED, "context.opinions");
        }
        Set<Long> allowedIds = new HashSet<>();
        for (AiOpinionContext opinion : context.opinions()) {
            if (opinion == null || opinion.opinionId() == null || !allowedIds.add(opinion.opinionId())) {
                fail(StructuredOutputSemanticFailureReason.ISSUE_INVALID, "context.opinions");
            }
        }
        validate(output, new StructuredOutputValidationContext(allowedIds));
    }

    private void validateIssue(
            IssueDetectionIssueOutput issue,
            StructuredOutputValidationContext validationContext,
            int index
    ) {
        String prefix = "issues[" + index + "]";
        if (issue == null || issue.type() == null) {
            fail(StructuredOutputSemanticFailureReason.ISSUE_INVALID, prefix);
        }
        if (!validText(issue.description(), MAX_DESCRIPTION_LENGTH)) {
            fail(StructuredOutputSemanticFailureReason.TEXT_INVALID, prefix + ".description");
        }
        if (issue.evidenceOpinionIds() == null) {
            fail(StructuredOutputSemanticFailureReason.COLLECTION_REQUIRED,
                    prefix + ".evidenceOpinionIds");
        }
        if (issue.evidenceOpinionIds().isEmpty()) {
            fail(StructuredOutputSemanticFailureReason.COLLECTION_EMPTY,
                    prefix + ".evidenceOpinionIds", null, 1, 0);
        }
        if (issue.evidenceOpinionIds().size() > MAX_EVIDENCE_PER_ISSUE) {
            fail(StructuredOutputSemanticFailureReason.COLLECTION_LIMIT_EXCEEDED,
                    prefix + ".evidenceOpinionIds", null,
                    MAX_EVIDENCE_PER_ISSUE, issue.evidenceOpinionIds().size());
        }
        if (issue.options() == null) {
            fail(StructuredOutputSemanticFailureReason.COLLECTION_REQUIRED, prefix + ".options");
        }

        Set<Long> evidenceIds = new HashSet<>();
        for (Long evidenceId : issue.evidenceOpinionIds()) {
            if (!evidenceIds.add(evidenceId)) {
                fail(StructuredOutputSemanticFailureReason.REFERENCE_DUPLICATED,
                        prefix + ".evidenceOpinionIds", evidenceId);
            }
            if (evidenceId == null || !validationContext.allowedResourceIds().contains(evidenceId)) {
                fail(StructuredOutputSemanticFailureReason.REFERENCE_NOT_ALLOWED,
                        prefix + ".evidenceOpinionIds", evidenceId);
            }
        }

        if (issue.type() == IssueType.CONFLICT) {
            validateConflict(issue, prefix);
        } else if (issue.type() == IssueType.GAP) {
            validateGap(issue, prefix);
        } else {
            fail(StructuredOutputSemanticFailureReason.ISSUE_INVALID, prefix + ".type");
        }
    }

    private void validateConflict(IssueDetectionIssueOutput issue, String prefix) {
        if (!validText(issue.question(), MAX_QUESTION_LENGTH)) {
            fail(StructuredOutputSemanticFailureReason.CONFLICT_FIELDS_INVALID,
                    prefix + ".question");
        }
        if (issue.options().size() < MIN_CONFLICT_OPTIONS
                || issue.options().size() > MAX_CONFLICT_OPTIONS) {
            fail(StructuredOutputSemanticFailureReason.CONFLICT_FIELDS_INVALID,
                    prefix + ".options", null, MIN_CONFLICT_OPTIONS, issue.options().size());
        }
        Set<String> options = new HashSet<>();
        for (String option : issue.options()) {
            if (!validText(option, MAX_OPTION_LENGTH) || !options.add(option.strip())) {
                fail(StructuredOutputSemanticFailureReason.CONFLICT_OPTION_INVALID,
                        prefix + ".options");
            }
        }
    }

    private void validateGap(IssueDetectionIssueOutput issue, String prefix) {
        if (issue.question() != null || !issue.options().isEmpty()) {
            fail(StructuredOutputSemanticFailureReason.GAP_FIELDS_INVALID, prefix);
        }
    }

    private boolean validText(String value, int maxLength) {
        return StringUtils.hasText(value) && value.length() <= maxLength;
    }

    private void fail(StructuredOutputSemanticFailureReason reason, String field) {
        throw new StructuredOutputSemanticException(reason, field);
    }

    private void fail(
            StructuredOutputSemanticFailureReason reason,
            String field,
            Long resourceId
    ) {
        throw new StructuredOutputSemanticException(reason, field, resourceId);
    }

    private void fail(
            StructuredOutputSemanticFailureReason reason,
            String field,
            Long resourceId,
            Integer expectedCount,
            Integer actualCount
    ) {
        throw new StructuredOutputSemanticException(
                reason, field, resourceId, expectedCount, actualCount);
    }
}
