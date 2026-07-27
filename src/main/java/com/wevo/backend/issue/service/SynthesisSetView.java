package com.wevo.backend.issue.service;

import com.wevo.backend.issue.domain.IssueStatus;
import com.wevo.backend.issue.domain.IssueType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 정리 세트 한 개의 조회 전용 표현. (API_SPEC §3.8.2)
 *
 * <p>issue 도메인이 조회 경계로 공개하는 값이며, 엔티티를 도메인 밖으로 내보내지 않는다.
 * 응답 DTO로의 변환은 호출 도메인(ai)이 담당한다.
 *
 * @param setId               세트의 외부 식별자 — 세트를 만든 AI 실행의 {@code requestId}
 * @param consensusSummary    합의점 요약 (정책서 §5.1.1)
 * @param issues              세트에 속한 쟁점 — 저장된 순서(sortOrder) 유지
 * @param inheritedGapAnswers 이 세트가 입력으로 승계한 이전 세트 답변의 원본 참조 (§3.8.1)
 */
public record SynthesisSetView(
        UUID setId,
        String consensusSummary,
        List<IssueView> issues,
        List<InheritedGapAnswerView> inheritedGapAnswers
) {

    /**
     * 쟁점 한 개의 조회 표현. 유형에 따라 채워지는 필드가 다르다.
     *
     * @param question          팀장이 결정할 질문 — {@code CONFLICT}만, {@code GAP}은 {@code null}
     * @param options           AI가 제시한 선택지 — {@code CONFLICT}만, {@code GAP}은 {@code null}
     * @param decision          결정 결과 — 결정 전이면 {@code null}
     * @param evidenceRequested 추가 근거 요청 여부 — {@code GAP}만, {@code CONFLICT}는 {@code null}
     * @param answer            보충 근거 답변 — 답변 전이면 {@code null}
     */
    public record IssueView(
            Long issueId,
            IssueType type,
            IssueStatus status,
            String description,
            List<RelatedOpinionView> relatedOpinions,
            String question,
            List<String> options,
            DecisionView decision,
            Boolean evidenceRequested,
            AnswerView answer
    ) {
    }

    /** 쟁점의 근거가 된 제출 의견. 작성자 이름은 세트 생성 당시 스냅샷이다. */
    public record RelatedOpinionView(Long opinionId, String authorName, String excerpt) {
    }

    /** CONFLICT 결정 결과. {@code selectedOption}과 {@code customInput} 중 하나만 값을 갖는다. */
    public record DecisionView(String selectedOption, String customInput, LocalDateTime decidedAt) {
    }

    /** GAP 보충 근거 답변. */
    public record AnswerView(Long answerId, String authorName, String content, LocalDateTime answeredAt) {
    }

    /** 승계 답변의 원본 참조 — 원본 쟁점·답변 ID로 근거를 추적할 수 있게 한다. */
    public record InheritedGapAnswerView(Long issueId, Long answerId) {
    }
}
