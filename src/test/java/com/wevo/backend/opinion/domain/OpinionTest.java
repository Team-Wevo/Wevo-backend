package com.wevo.backend.opinion.domain;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OpinionTest {

    @Test
    @DisplayName("제출 시각이 없으면 상태를 변경하지 않고 제출을 거부한다")
    void submit_nullSubmittedAt_keepsDraftStatus() {
        Opinion opinion = Opinion.builder()
                .content("타겟을 공모전 참가 대학생 팀으로 좁히는 게 좋겠습니다.")
                .status(OpinionStatus.DRAFT)
                .build();

        assertThatNullPointerException()
                .isThrownBy(() -> opinion.submit(null))
                .withMessage("submittedAt must not be null");

        assertThat(opinion.getStatus()).isEqualTo(OpinionStatus.DRAFT);
        assertThat(opinion.getSubmittedAt()).isNull();
    }

    @Test
    @DisplayName("레거시 제출 의견(제출본 컬럼 없음)은 재편집 시 이전 본문을 제출본으로 백필한다")
    void updateContent_legacySubmitted_backfillsSubmittedContent() {
        String submittedBody = "제출 당시 작성했던 스무 자 이상의 의견 본문입니다.";
        // 재제출 모델 도입 전 제출된 행 — submit() 을 거치지 않아 submittedContent 가 비어 있다.
        Opinion legacy = Opinion.builder()
                .content(submittedBody)
                .status(OpinionStatus.SUBMITTED)
                .submittedAt(LocalDateTime.of(2026, 7, 14, 12, 5))
                .build();

        legacy.updateContent("재편집으로 바뀐 새 작업본입니다. 스무 자 이상 작성.");

        // 팀 목록에는 작업본이 아니라 제출 당시 본문이 유지되어야 한다 (§4.1)
        assertThat(legacy.getSubmittedContentOrLegacy()).isEqualTo(submittedBody);
        assertThat(legacy.hasUnsubmittedChanges()).isTrue();
    }

    @Test
    @DisplayName("재편집 없이 레거시 제출 의견을 다시 제출하면 아무것도 바뀌지 않는다 (멱등)")
    void submit_legacyUnchanged_isIdempotent() {
        LocalDateTime firstSubmittedAt = LocalDateTime.of(2026, 7, 14, 12, 5);
        Opinion legacy = Opinion.builder()
                .content("제출 당시 작성했던 스무 자 이상의 의견 본문입니다.")
                .status(OpinionStatus.SUBMITTED)
                .submittedAt(firstSubmittedAt)
                .build();

        legacy.submit(LocalDateTime.of(2026, 7, 15, 9, 0));

        assertThat(legacy.getSubmittedAt()).isEqualTo(firstSubmittedAt);
        assertThat(legacy.hasUnsubmittedChanges()).isFalse();
    }
}
