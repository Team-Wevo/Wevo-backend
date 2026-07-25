package com.wevo.backend.ai.service;

import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputValidator;
import com.wevo.backend.ai.service.SynthesisAiOutput.IssueOut;
import com.wevo.backend.issue.domain.IssueType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * AI 의견 정리 출력의 <b>의미 검증</b>. (§5.1.7 상한, §5.1.3 CONFLICT 규칙, AI 출력 검증 §7)
 *
 * <p>스키마(형식) 검증을 통과한 출력이라도 개수 상한·CONFLICT/GAP 규칙·존재하지 않는 의견 참조는
 * 막아야 한다. 위반 시 {@link StructuredOutputSemanticException}을 던지면 gateway가 교정 재시도
 * 후에도 실패하면 작업을 실패로 종료시킨다(검증 안 된 출력을 저장하지 않는다).
 *
 * <p>참조 의견 검증은 요청별 허용 집합({@link StructuredOutputValidationContext#allowedResourceIds})으로
 * 하므로 이 검증기는 무상태 싱글턴이다.
 */
@Component
public class SynthesisOutputValidator implements StructuredOutputValidator<SynthesisAiOutput> {

    static final int MAX_CONSENSUS_LENGTH = 2_000;
    static final int MAX_DESCRIPTION_LENGTH = 2_000;
    static final int MAX_QUESTION_LENGTH = 500;
    static final int MAX_OPTION_LENGTH = 200;
    static final int MIN_CONFLICT_OPTIONS = 2;
    static final int MAX_CONFLICT_OPTIONS = 5;

    @Override
    public void validate(SynthesisAiOutput output, StructuredOutputValidationContext context) {
        requireText(output.consensusSummary(), MAX_CONSENSUS_LENGTH);

        List<IssueOut> issues = output.issues();
        if (issues == null || issues.size() > SynthesisContract.MAX_TOTAL_ISSUES) {
            throw reject();
        }

        long conflictCount = issues.stream().filter(issue -> issue.type() == IssueType.CONFLICT).count();
        long gapCount = issues.stream().filter(issue -> issue.type() == IssueType.GAP).count();
        if (conflictCount > SynthesisContract.MAX_CONFLICT_ISSUES
                || gapCount > SynthesisContract.MAX_GAP_ISSUES) {
            throw reject();
        }

        for (IssueOut issue : issues) {
            validateIssue(issue, context);
        }
    }

    private void validateIssue(IssueOut issue, StructuredOutputValidationContext context) {
        if (issue.type() == null) {
            throw reject();
        }
        requireText(issue.description(), MAX_DESCRIPTION_LENGTH);
        validateRelatedOpinions(issue, context);

        if (issue.type() == IssueType.CONFLICT) {
            validateConflict(issue);
        } else {
            validateGap(issue);
        }
    }

    private void validateConflict(IssueOut issue) {
        requireText(issue.question(), MAX_QUESTION_LENGTH);
        List<String> options = issue.options();
        if (options == null
                || options.size() < MIN_CONFLICT_OPTIONS
                || options.size() > MAX_CONFLICT_OPTIONS) {
            throw reject();
        }
        for (String option : options) {
            requireText(option, MAX_OPTION_LENGTH);
        }
    }

    private void validateGap(IssueOut issue) {
        // GAP은 질문·선택지를 갖지 않는다 (§5.1.3 — 결정은 CONFLICT만).
        // 잘못된 출력(질문·선택지가 있는 GAP)은 저장 단계에서 버리기 전에 여기서 교정 재시도시킨다.
        if (issue.question() != null && !issue.question().isBlank()) {
            throw reject();
        }
        if (issue.options() != null && !issue.options().isEmpty()) {
            throw reject();
        }
    }

    private void validateRelatedOpinions(IssueOut issue, StructuredOutputValidationContext context) {
        List<Long> relatedOpinionIds = issue.relatedOpinionIds();
        if (relatedOpinionIds == null || relatedOpinionIds.isEmpty()) {
            throw reject();
        }
        for (Long opinionId : relatedOpinionIds) {
            context.requireAllowedResourceId(opinionId);
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
