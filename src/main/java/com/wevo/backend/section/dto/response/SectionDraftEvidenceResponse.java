package com.wevo.backend.section.dto.response;

import com.wevo.backend.section.service.SectionDraftEvidenceView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 초안 근거 보기 응답. (API_SPEC §3.7.3)
 *
 * <p>문장 단위가 아니라 초안 <b>생성 시점</b>에 사용된 의견·합의점·쟁점 결정·보충 근거 목록을
 * 그대로 반환한다 — 세트가 나중에 대체돼도 이 초안의 근거는 유실되지 않는다.
 *
 * <p>목록 셋은 비어 있어도 배열로 내려가 필수지만, {@code consensusSummary} 는 합의점이 정리되지
 * 않은 초안에서 비므로 필수에서 뺐다.
 */
@Schema(requiredProperties = {"opinions", "decisions", "gapAnswers"})
public record SectionDraftEvidenceResponse(
        String consensusSummary,
        List<Opinion> opinions,
        List<Decision> decisions,
        List<GapAnswer> gapAnswers
) {

    public static SectionDraftEvidenceResponse of(SectionDraftEvidenceView view) {
        return new SectionDraftEvidenceResponse(
                view.consensusSummary(),
                view.opinions().stream()
                        .map(item -> new Opinion(item.opinionId(), item.authorName(), item.content()))
                        .toList(),
                view.decisions().stream()
                        .map(item -> new Decision(item.issueId(), item.question(), item.decision()))
                        .toList(),
                view.gapAnswers().stream()
                        .map(item -> new GapAnswer(
                                item.sourceIssueId(),
                                item.answerId(),
                                item.authorName(),
                                item.content(),
                                item.answeredAt(),
                                item.inherited()))
                        .toList());
    }

    @Schema(requiredProperties = {"opinionId", "authorName", "content"})

    public record Opinion(Long opinionId, String authorName, String content) {
    }

    @Schema(requiredProperties = {"issueId", "question", "decision"})
    public record Decision(Long issueId, String question, String decision) {
    }

    /**
     * @param issueId  원본 쟁점 참조 — 승계 답변이면 이전 세트의 쟁점 ID
     * @param answerId 원본 답변 참조 — 승계 답변이면 이전 세트의 답변 ID
     * @param inherited 이전 세트에서 승계된 답변이면 {@code true}
     */
    @Schema(requiredProperties = {"issueId", "answerId", "authorName", "content", "answeredAt", "inherited"})
    public record GapAnswer(
            Long issueId,
            Long answerId,
            String authorName,
            String content,
            LocalDateTime answeredAt,
            boolean inherited
    ) {
    }
}
