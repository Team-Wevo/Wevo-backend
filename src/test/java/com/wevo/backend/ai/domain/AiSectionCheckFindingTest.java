package com.wevo.backend.ai.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class AiSectionCheckFindingTest {

    private final AiSectionCheck sectionCheck = mock(AiSectionCheck.class);

    @Test
    void acceptsFindingTextAtMaximumLengths() {
        assertThatCode(() -> finding(
                "가".repeat(AiSectionCheckFinding.MAX_TARGET_EXCERPT_LENGTH),
                "나".repeat(AiSectionCheckFinding.MAX_COMMENT_LENGTH),
                "다".repeat(AiSectionCheckFinding.MAX_SUGGESTION_LENGTH)
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsFindingTextOverMaximumLengths() {
        assertThatThrownBy(() -> finding(
                "가".repeat(AiSectionCheckFinding.MAX_TARGET_EXCERPT_LENGTH + 1),
                "의견",
                "제안"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> finding(
                "발췌",
                "나".repeat(AiSectionCheckFinding.MAX_COMMENT_LENGTH + 1),
                "제안"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> finding(
                "발췌",
                "의견",
                "다".repeat(AiSectionCheckFinding.MAX_SUGGESTION_LENGTH + 1)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private AiSectionCheckFinding finding(
            String targetExcerpt,
            String comment,
            String suggestion
    ) {
        return AiSectionCheckFinding.builder()
                .sectionCheck(sectionCheck)
                .type(AiSectionFindingType.UNCLEAR_SENTENCE)
                .targetExcerpt(targetExcerpt)
                .comment(comment)
                .suggestion(suggestion)
                .sortOrder(1)
                .build();
    }
}
