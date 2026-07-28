package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
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
            fail();
        }
        List<IssueDetectionIssueOutput> issues = output.issues();
        if (issues.size() > MAX_ISSUES) {
            fail();
        }

        int conflicts = 0;
        int gaps = 0;
        for (IssueDetectionIssueOutput issue : issues) {
            validateIssue(issue, validationContext);
            if (issue.type() == IssueType.CONFLICT) {
                conflicts++;
            } else if (issue.type() == IssueType.GAP) {
                gaps++;
            }
        }
        if (conflicts > MAX_CONFLICTS || gaps > MAX_GAPS) {
            fail();
        }
    }

    public void validate(IssueDetectionOutput output, IssueDetectionContext context) {
        if (context == null || context.opinions() == null) {
            fail();
        }
        Set<Long> allowedIds = new HashSet<>();
        for (AiOpinionContext opinion : context.opinions()) {
            if (opinion == null || opinion.opinionId() == null || !allowedIds.add(opinion.opinionId())) {
                fail();
            }
        }
        validate(output, new StructuredOutputValidationContext(allowedIds));
    }

    private void validateIssue(
            IssueDetectionIssueOutput issue,
            StructuredOutputValidationContext validationContext
    ) {
        if (issue == null
                || issue.type() == null
                || !validText(issue.description(), MAX_DESCRIPTION_LENGTH)
                || issue.evidenceOpinionIds() == null
                || issue.evidenceOpinionIds().isEmpty()
                || issue.evidenceOpinionIds().size() > MAX_EVIDENCE_PER_ISSUE
                || issue.options() == null) {
            fail();
        }

        Set<Long> evidenceIds = new HashSet<>();
        for (Long evidenceId : issue.evidenceOpinionIds()) {
            if (!evidenceIds.add(evidenceId)) {
                fail();
            }
            validationContext.requireAllowedResourceId(evidenceId);
        }

        if (issue.type() == IssueType.CONFLICT) {
            validateConflict(issue);
        } else if (issue.type() == IssueType.GAP) {
            validateGap(issue);
        } else {
            fail();
        }
    }

    private void validateConflict(IssueDetectionIssueOutput issue) {
        if (!validText(issue.question(), MAX_QUESTION_LENGTH)
                || issue.options().size() < MIN_CONFLICT_OPTIONS
                || issue.options().size() > MAX_CONFLICT_OPTIONS) {
            fail();
        }
        Set<String> options = new HashSet<>();
        for (String option : issue.options()) {
            if (!validText(option, MAX_OPTION_LENGTH) || !options.add(option.strip())) {
                fail();
            }
        }
    }

    private void validateGap(IssueDetectionIssueOutput issue) {
        if (issue.question() != null || !issue.options().isEmpty()) {
            fail();
        }
    }

    private boolean validText(String value, int maxLength) {
        return StringUtils.hasText(value) && value.length() <= maxLength;
    }

    private void fail() {
        throw new StructuredOutputSemanticException();
    }
}
