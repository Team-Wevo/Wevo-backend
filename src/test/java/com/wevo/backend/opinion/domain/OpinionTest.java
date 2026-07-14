package com.wevo.backend.opinion.domain;

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
}
