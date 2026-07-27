package com.wevo.backend.issue.service;

import com.wevo.backend.issue.domain.IssueAnswer;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.domain.SynthesisInheritedGapAnswer;
import com.wevo.backend.issue.domain.SynthesisSet;
import com.wevo.backend.issue.repository.IssueAnswerRepository;
import com.wevo.backend.issue.repository.SynthesisInheritedGapAnswerRepository;
import com.wevo.backend.issue.repository.SynthesisSetRepository;
import com.wevo.backend.project.service.VerifiedSectionAccess;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 정리 결과 존재 여부와 AI 입력용 current set/GAP 답변을 공개하는 읽기 경계. */
@Service
@Transactional(readOnly = true)
public class SynthesisSetQueryService {

    private final SynthesisSetRepository synthesisSetRepository;
    private final IssueAnswerRepository issueAnswerRepository;
    private final SynthesisInheritedGapAnswerRepository inheritedGapAnswerRepository;

    public SynthesisSetQueryService(
            SynthesisSetRepository synthesisSetRepository,
            IssueAnswerRepository issueAnswerRepository,
            SynthesisInheritedGapAnswerRepository inheritedGapAnswerRepository
    ) {
        this.synthesisSetRepository = synthesisSetRepository;
        this.issueAnswerRepository = issueAnswerRepository;
        this.inheritedGapAnswerRepository = inheritedGapAnswerRepository;
    }

    public boolean existsForSection(Long projectSectionId) {
        return synthesisSetRepository.existsByProjectSectionId(projectSectionId);
    }

    /**
     * {@code createdAt DESC, id DESC} 기준 current set과 직접·승계 GAP 답변 합집합을 반환한다.
     *
     * <p>current set이나 참조 원본이 누락된 비정상 상태를 빈 값으로 바꾸지 않는다.
     */
    public CurrentSynthesisContext getCurrentForAiContext(
            VerifiedSectionAccess sectionAccess
    ) {
        if (sectionAccess == null) {
            throw new IllegalArgumentException("검증된 section 접근 정보는 필수입니다.");
        }
        Long sectionId = sectionAccess.sectionId();
        SynthesisSet current = synthesisSetRepository
                .findTopByProjectSectionIdOrderByCreatedAtDescIdDesc(sectionId)
                .orElseThrow(() -> new IllegalStateException(
                        "AI context에 필요한 current synthesis set이 없습니다."));

        List<GapAnswerContext> sorted = loadValidatedGapAnswers(current, sectionId).values().stream()
                .map(this::toContext)
                .sorted(Comparator.comparing(GapAnswerContext::answeredAt)
                        .thenComparing(GapAnswerContext::answerId))
                .toList();
        return new CurrentSynthesisContext(
                current.getId(),
                current.getOpinionGateGeneration(),
                current.getConsensusSummary(),
                sorted);
    }

    private Map<Long, IssueAnswer> loadValidatedGapAnswers(
            SynthesisSet current,
            Long expectedSectionId
    ) {
        if (current == null
                || current.getId() == null
                || expectedSectionId == null
                || !expectedSectionId.equals(current.getProjectSectionId())) {
            throw new IllegalStateException("current synthesis set의 section 소속이 일치하지 않습니다.");
        }
        Long currentSetId = current.getId();

        List<IssueAnswer> direct =
                issueAnswerRepository.findAllWithIssueBySynthesisSetId(currentSetId);
        List<SynthesisInheritedGapAnswer> inherited =
                inheritedGapAnswerRepository.findAllBySynthesisSet_Id(currentSetId);
        for (SynthesisInheritedGapAnswer reference : inherited) {
            validateInheritedReference(reference, currentSetId);
        }

        List<Long> inheritedIds = inherited.stream()
                .map(SynthesisInheritedGapAnswer::getSourceAnswerId)
                .distinct()
                .toList();
        Set<Long> inheritedIdSet = new HashSet<>(inheritedIds);
        List<IssueAnswer> sources = inheritedIds.isEmpty()
                ? List.of()
                : issueAnswerRepository.findAllWithIssueByIdIn(inheritedIds);

        Map<Long, IssueAnswer> sourceById = new HashMap<>();
        for (IssueAnswer answer : sources) {
            if (answer == null
                    || answer.getId() == null
                    || !inheritedIdSet.contains(answer.getId())) {
                throw new IllegalStateException("승계 GAP 답변의 원본 참조가 누락되었습니다.");
            }
            sourceById.put(answer.getId(), answer);
        }
        if (sourceById.size() != inheritedIds.size()) {
            throw new IllegalStateException("승계 GAP 답변의 원본 참조가 누락되었습니다.");
        }

        Map<Long, IssueAnswer> unique = new HashMap<>();
        for (IssueAnswer answer : direct) {
            validateDirectAnswer(answer, currentSetId, expectedSectionId);
            unique.put(answer.getId(), answer);
        }
        for (SynthesisInheritedGapAnswer reference : inherited) {
            IssueAnswer answer = sourceById.get(reference.getSourceAnswerId());
            validateInheritedAnswer(
                    answer,
                    expectedSectionId,
                    reference.getSourceIssueId());
            unique.putIfAbsent(answer.getId(), answer);
        }
        return unique;
    }

    private void validateInheritedReference(
            SynthesisInheritedGapAnswer reference,
            Long expectedSetId
    ) {
        if (reference == null
                || reference.getSynthesisSet() == null
                || !expectedSetId.equals(reference.getSynthesisSet().getId())
                || reference.getSourceAnswerId() == null
                || reference.getSourceIssueId() == null) {
            throw new IllegalStateException("승계 GAP 답변 참조의 무결성이 깨졌습니다.");
        }
    }

    private void validateDirectAnswer(
            IssueAnswer answer,
            Long expectedSetId,
            Long expectedSectionId
    ) {
        if (isInvalidAnswer(answer)
                || !expectedSetId.equals(answer.getIssue().getSynthesisSet().getId())
                || !expectedSectionId.equals(
                        answer.getIssue().getSynthesisSet().getProjectSectionId())) {
            throw new IllegalStateException("AI context GAP 답변 참조의 무결성이 깨졌습니다.");
        }
    }

    private void validateInheritedAnswer(
            IssueAnswer answer,
            Long expectedSectionId,
            Long expectedIssueId
    ) {
        if (isInvalidAnswer(answer)
                || !expectedSectionId.equals(
                        answer.getIssue().getSynthesisSet().getProjectSectionId())
                || !expectedIssueId.equals(answer.getIssue().getId())) {
            throw new IllegalStateException("AI context GAP 답변 참조의 무결성이 깨졌습니다.");
        }
    }

    private boolean isInvalidAnswer(IssueAnswer answer) {
        return answer == null
                || answer.getId() == null
                || answer.getIssue() == null
                || answer.getIssue().getSynthesisSet() == null
                || answer.getIssue().getType() != IssueType.GAP
                || answer.getAnsweredAt() == null
                || answer.getContent() == null
                || answer.getContent().isBlank();
    }

    private GapAnswerContext toContext(IssueAnswer answer) {
        return new GapAnswerContext(
                answer.getIssue().getId(),
                answer.getId(),
                answer.getContent(),
                answer.getAnsweredAt());
    }

    /**
     * 섹션의 현재 정리 세트를 기준으로 <b>AI 재정리 입력에 포함할 GAP 답변 집합</b>을 조회한다. (§3.8.1)
     *
     * <p>집합은 <b>현재 세트의 직접 답변</b>과 <b>현재 세트가 승계한 이전 답변의 원본</b>의 합집합이며
     * {@code answerId} 기준으로 중복을 제거한다 — 여러 세대에 걸쳐 재정리해도 앞선 답변이 유실되지
     * 않는다. 정리 이력이 없으면 빈 목록을 반환한다.
     *
     * <p>반환 순서는 {@code answerId} 오름차순으로 고정한다 — 입력 스냅샷 해시가 조회 순서에
     * 흔들리지 않게 하기 위한 canonical 정렬이다.
     */
    public List<GapAnswerInputView> findCurrentGapAnswerInput(Long projectSectionId) {
        SynthesisSet current = synthesisSetRepository
                .findTopByProjectSectionIdOrderByCreatedAtDescIdDesc(projectSectionId)
                .orElse(null);
        if (current == null) {
            return List.of();
        }

        return loadValidatedGapAnswers(current, projectSectionId).values().stream()
                .map(answer -> new GapAnswerInputView(
                        answer.getId(),
                        answer.getIssue().getId(),
                        answer.getAuthorNameSnapshot(),
                        answer.getContent()))
                .sorted(Comparator.comparing(GapAnswerInputView::answerId))
                .toList();
    }
}
