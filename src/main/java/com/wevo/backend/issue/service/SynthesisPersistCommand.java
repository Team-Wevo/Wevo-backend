package com.wevo.backend.issue.service;

import com.wevo.backend.issue.domain.IssueType;
import java.util.List;
import java.util.UUID;

/**
 * 성공한 AI 정리 결과를 issue 도메인에 저장하기 위한 명령. (§3.8.1)
 *
 * <p>ai 도메인이 검증을 마친 AI 출력을 이 명령으로 변환해 {@link SynthesisResultWriteService}에
 * 넘긴다. 쟁점·선택지·관련 의견의 {@code sortOrder}는 목록 순서로 저장 측이 부여한다.
 *
 * @param requestId            정리 실행 ID = 세트 식별자
 * @param projectSectionId     대상 섹션 ID
 * @param opinionGateGeneration 입력 스냅샷에 묶인 마감 세대
 * @param consensusSummary     합의점 요약 (공백 불가)
 * @param issues               쟁점 목록 (목록 순서가 표시 순서)
 * @param inheritedGapAnswers  이 세트가 입력으로 승계한 이전 세트 답변의 원본 참조
 */
public record SynthesisPersistCommand(
        UUID requestId,
        Long projectSectionId,
        long opinionGateGeneration,
        String consensusSummary,
        List<IssueSpec> issues,
        List<InheritedGapAnswerRef> inheritedGapAnswers
) {

    /**
     * @param type            CONFLICT / GAP
     * @param description     충돌 설명 / 부족한 정보
     * @param question        팀장이 결정할 질문 — CONFLICT만 (GAP은 {@code null})
     * @param options         선택지 — CONFLICT만 (GAP은 빈 목록)
     * @param relatedOpinions 근거가 된 제출 의견 발췌
     */
    public record IssueSpec(
            IssueType type,
            String description,
            String question,
            List<String> options,
            List<RelatedOpinionSpec> relatedOpinions
    ) {
    }

    /**
     * @param opinionId  근거 의견 ID
     * @param authorUserId 작성자 ID
     * @param authorName 작성자 이름 스냅샷
     * @param excerpt    의견 본문 발췌
     */
    public record RelatedOpinionSpec(
            Long opinionId,
            Long authorUserId,
            String authorName,
            String excerpt
    ) {
    }

    /**
     * @param sourceIssueId  원본 쟁점 ID
     * @param sourceAnswerId 원본 답변 ID
     */
    public record InheritedGapAnswerRef(Long sourceIssueId, Long sourceAnswerId) {
    }
}
