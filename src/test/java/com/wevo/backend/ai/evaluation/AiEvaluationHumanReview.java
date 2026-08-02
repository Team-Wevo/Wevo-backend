package com.wevo.backend.ai.evaluation;

import java.time.Instant;
import java.util.Map;

/** 자동 gate와 독립적으로 승인자가 기록하는 fixture별 사람 평가 결과. */
public record AiEvaluationHumanReview(
        Status status,
        String reviewer,
        Instant reviewedAt,
        Map<String, Score> fixtureScores
) {

    public AiEvaluationHumanReview {
        if (status == null) {
            throw new IllegalArgumentException("사람 평가 status는 필수입니다.");
        }
        fixtureScores = fixtureScores == null ? Map.of() : Map.copyOf(fixtureScores);
        if (status == Status.COMPLETED
                && (reviewer == null || reviewer.isBlank() || reviewedAt == null)) {
            throw new IllegalArgumentException("완료된 사람 평가에는 reviewer와 reviewedAt이 필요합니다.");
        }
        if (status == Status.PENDING
                && (reviewer != null || reviewedAt != null || !fixtureScores.isEmpty())) {
            throw new IllegalArgumentException("대기 중인 사람 평가에는 결과를 기록할 수 없습니다.");
        }
    }

    public static AiEvaluationHumanReview pending() {
        return new AiEvaluationHumanReview(Status.PENDING, null, null, Map.of());
    }

    public record Score(int accuracy, int grounding, int neutrality, int clarity) {
        public Score {
            requireRange(accuracy);
            requireRange(grounding);
            requireRange(neutrality);
            requireRange(clarity);
        }

        private static void requireRange(int value) {
            if (value < 1 || value > 5) {
                throw new IllegalArgumentException("사람 평가 점수는 1 이상 5 이하여야 합니다.");
            }
        }
    }

    public enum Status {
        PENDING,
        COMPLETED
    }
}
