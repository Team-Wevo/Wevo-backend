package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wevo.backend.ai.client.StructuredOutputSemanticException;
import com.wevo.backend.ai.client.StructuredOutputValidationContext;
import com.wevo.backend.ai.domain.AiSectionCheckFinding;
import com.wevo.backend.ai.domain.AiSectionFindingType;
import com.wevo.backend.ai.dto.model.DraftReviewFindingOutput;
import com.wevo.backend.ai.dto.model.DraftReviewOutput;
import com.wevo.backend.ai.dto.model.DraftReviewRewriteOutput;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DraftReviewOutputValidatorTest {

    private static final String CURRENT = "현재 문장은 독자에게 모호합니다.";
    private static final String PREREQUISITE = "앞선 섹션은 국내 사용자만 대상으로 합니다.";

    private final DraftReviewOutputValidator validator =
            new DraftReviewOutputValidator();
    private final StructuredOutputValidationContext context =
            StructuredOutputValidationContext.forSourceContents(
                    CURRENT, Set.of(CURRENT, PREREQUISITE));

    @Test
    void acceptsAllFourFindingTypesAndFullRewrite() {
        List<DraftReviewFindingOutput> findings =
                Arrays.stream(AiSectionFindingType.values())
                        .map(type -> finding(
                                type,
                                type == AiSectionFindingType.PREREQUISITE_CONFLICT
                                        ? "국내 사용자만"
                                        : "독자에게 모호"))
                        .toList();
        DraftReviewOutput output = new DraftReviewOutput(
                findings,
                new DraftReviewRewriteOutput("현재 문장은 대상과 의미가 명확합니다.", 4)
        );

        assertThatCode(() -> validator.validate(output, context))
                .doesNotThrowAnyException();
    }

    @Test
    void noFindingIsNormalOnlyWhenRewriteKeepsCurrentContent() {
        assertThatCode(() -> validator.validate(
                new DraftReviewOutput(
                        List.of(),
                        new DraftReviewRewriteOutput(CURRENT, 0)),
                context
        )).doesNotThrowAnyException();

        assertRejected(new DraftReviewOutput(
                List.of(),
                new DraftReviewRewriteOutput("임의로 바뀐 본문", 0)));
        assertRejected(new DraftReviewOutput(
                List.of(),
                new DraftReviewRewriteOutput(CURRENT, 1)));
    }

    @Test
    void rejectsExcerptOutsideReviewedDraftsAndMissingValues() {
        assertRejected(new DraftReviewOutput(
                List.of(finding(AiSectionFindingType.UNCLEAR_SENTENCE, "존재하지 않는 문장")),
                new DraftReviewRewriteOutput("개선된 본문", 1)));
        assertRejected(new DraftReviewOutput(
                List.of(new DraftReviewFindingOutput(
                        AiSectionFindingType.READER_QUESTION,
                        "독자에게 모호",
                        " ",
                        "명시하세요.")),
                new DraftReviewRewriteOutput("개선된 본문", 1)));
    }

    @Test
    void rejectsBlankOverlongAndIncoherentRewrite() {
        assertRejected(new DraftReviewOutput(
                List.of(finding(AiSectionFindingType.HIDDEN_ASSUMPTION, "독자에게 모호")),
                new DraftReviewRewriteOutput(" ", 1)));
        assertRejected(new DraftReviewOutput(
                List.of(finding(AiSectionFindingType.HIDDEN_ASSUMPTION, "독자에게 모호")),
                new DraftReviewRewriteOutput("a".repeat(10_001), 1)));
        assertRejected(new DraftReviewOutput(
                List.of(finding(AiSectionFindingType.HIDDEN_ASSUMPTION, "독자에게 모호")),
                new DraftReviewRewriteOutput(CURRENT, 0)));
    }

    @Test
    void rejectsOverlongFindingText() {
        String overlongExcerpt =
                "가".repeat(AiSectionCheckFinding.MAX_TARGET_EXCERPT_LENGTH + 1);
        StructuredOutputValidationContext overlongExcerptContext =
                StructuredOutputValidationContext.forSourceContents(
                        overlongExcerpt, Set.of(overlongExcerpt));
        assertThatThrownBy(() -> validator.validate(
                new DraftReviewOutput(
                        List.of(finding(
                                AiSectionFindingType.UNCLEAR_SENTENCE,
                                overlongExcerpt)),
                        new DraftReviewRewriteOutput("개선된 본문", 1)),
                overlongExcerptContext
        )).isInstanceOf(StructuredOutputSemanticException.class);
        assertRejected(new DraftReviewOutput(
                List.of(finding(
                        AiSectionFindingType.UNCLEAR_SENTENCE,
                        "독자에게 모호",
                        "가".repeat(AiSectionCheckFinding.MAX_COMMENT_LENGTH + 1),
                        "구체적으로 수정하세요.")),
                new DraftReviewRewriteOutput("개선된 본문", 1)));
        assertRejected(new DraftReviewOutput(
                List.of(finding(
                        AiSectionFindingType.UNCLEAR_SENTENCE,
                        "독자에게 모호",
                        "점검 의견",
                        "가".repeat(AiSectionCheckFinding.MAX_SUGGESTION_LENGTH + 1))),
                new DraftReviewRewriteOutput("개선된 본문", 1)));
    }

    private DraftReviewFindingOutput finding(
            AiSectionFindingType type,
            String excerpt
    ) {
        return finding(type, excerpt, "점검 의견", "구체적으로 수정하세요.");
    }

    private DraftReviewFindingOutput finding(
            AiSectionFindingType type,
            String excerpt,
            String comment,
            String suggestion
    ) {
        return new DraftReviewFindingOutput(
                type, excerpt, comment, suggestion);
    }

    private void assertRejected(DraftReviewOutput output) {
        assertThatThrownBy(() -> validator.validate(output, context))
                .isInstanceOf(StructuredOutputSemanticException.class);
    }
}
