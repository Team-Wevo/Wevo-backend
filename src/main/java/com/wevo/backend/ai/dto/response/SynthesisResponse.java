package com.wevo.backend.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wevo.backend.ai.domain.AiRequestStatus;
import com.wevo.backend.issue.domain.IssueStatus;
import com.wevo.backend.issue.domain.IssueType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 합의점·쟁점 조회 응답. (API_SPEC §3.8.2)
 *
 * <p><b>최신 실행({@code latestJob})과 현재 세트({@code currentSet})를 분리</b>한다 — 재정리가
 * 진행 중이거나 실패해도 기존 성공 세트는 그대로 유효하기 때문이다(대체는 성공 시에만 — §3.8.1).
 * 클라이언트는 {@code latestJob.status}와 {@code synthesisStale}로 "갱신 중 / 실패" 표시를 만든다.
 *
 * @param exists         정리 <b>실행</b> 이력 존재 여부 — {@code false}면(실행 전, 오류 아님) 나머지 필드는 생략
 * @param synthesisStale 재정리 필요 여부 (§4.5 재오픈 · §5.1.6 늦은 GAP 답변)
 * @param currentSet     최신 성공 실행의 결과 — 성공 이력이 없으면 생략
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SynthesisResponse(
        boolean exists,
        Boolean synthesisStale,
        LatestJobResponse latestJob,
        CurrentSetResponse currentSet
) {

    /** 정리를 한 번도 실행하지 않은 섹션의 응답. */
    public static SynthesisResponse notExecuted() {
        return new SynthesisResponse(false, null, null, null);
    }

    public static SynthesisResponse of(
            boolean synthesisStale,
            LatestJobResponse latestJob,
            CurrentSetResponse currentSet
    ) {
        return new SynthesisResponse(true, synthesisStale, latestJob, currentSet);
    }

    /**
     * 최신 실행 상태.
     *
     * @param failure 실패 사유 — 진행 중·성공이면 {@code null}
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LatestJobResponse(
            UUID requestId,
            AiRequestStatus status,
            FailureResponse failure
    ) {
    }

    /**
     * 작업 실패 사유.
     *
     * @param errorCode 외부 실패 코드 (`AI0xx`)
     * @param message   사용자에게 그대로 노출해도 되는 사유 — 제공자 원문은 담지 않는다 (CLAUDE.md §7)
     */
    public record FailureResponse(String errorCode, String message) {
    }

    /**
     * 현재 정리 세트.
     *
     * @param setId 세트 식별자 = 세트를 만든 실행의 {@code requestId}
     */
    public record CurrentSetResponse(
            UUID setId,
            String consensusSummary,
            List<IssueResponse> issues,
            List<InheritedGapAnswerResponse> inheritedGapAnswers
    ) {
    }

    /**
     * 쟁점 하나. 유형에 해당하지 않는 필드는 생략된다 —
     * {@code CONFLICT}는 {@code question}·{@code options}·{@code decision},
     * {@code GAP}은 {@code evidenceRequested}·{@code answer}를 갖는다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IssueResponse(
            Long issueId,
            IssueType type,
            IssueStatus status,
            String description,
            List<RelatedOpinionResponse> relatedOpinions,
            String question,
            List<String> options,
            DecisionResponse decision,
            Boolean evidenceRequested,
            AnswerResponse answer
    ) {
    }

    public record RelatedOpinionResponse(Long opinionId, String authorName, String excerpt) {
    }

    /** 결정 결과 — {@code selectedOption}과 {@code customInput} 중 값이 있는 쪽만 담긴다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DecisionResponse(
            String selectedOption,
            String customInput,
            LocalDateTime decidedAt
    ) {
    }

    public record AnswerResponse(
            Long answerId,
            String authorName,
            String content,
            LocalDateTime answeredAt
    ) {
    }

    /** 이 세트 생성에 입력으로 승계된 이전 세트 답변의 원본 참조. */
    public record InheritedGapAnswerResponse(Long issueId, Long answerId) {
    }
}
