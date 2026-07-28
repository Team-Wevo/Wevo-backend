package com.wevo.backend.issue.service;

import com.wevo.backend.issue.domain.IssueAnswer;
import com.wevo.backend.issue.domain.Issue;
import com.wevo.backend.issue.domain.IssueDecision;
import com.wevo.backend.issue.domain.IssueRelatedOpinion;
import com.wevo.backend.issue.domain.IssueStatus;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.domain.SynthesisConsensusEvidence;
import com.wevo.backend.issue.domain.SynthesisInheritedGapAnswer;
import com.wevo.backend.issue.domain.SynthesisSet;
import com.wevo.backend.issue.repository.IssueAnswerRepository;
import com.wevo.backend.issue.repository.IssueDecisionRepository;
import com.wevo.backend.issue.repository.IssueRelatedOpinionRepository;
import com.wevo.backend.issue.repository.IssueRepository;
import com.wevo.backend.issue.repository.SynthesisConsensusEvidenceRepository;
import com.wevo.backend.issue.repository.SynthesisInheritedGapAnswerRepository;
import com.wevo.backend.issue.repository.SynthesisSetRepository;
import com.wevo.backend.project.service.VerifiedSectionAccess;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 정리 결과 존재 여부와 AI 입력용 current set/GAP 답변을 공개하는 읽기 경계. */
@Service
@Transactional(readOnly = true)
public class SynthesisSetQueryService {

    private final SynthesisSetRepository synthesisSetRepository;
    private final IssueAnswerRepository issueAnswerRepository;
    private final SynthesisInheritedGapAnswerRepository inheritedGapAnswerRepository;
    private final IssueRepository issueRepository;
    private final IssueDecisionRepository issueDecisionRepository;
    private final IssueRelatedOpinionRepository issueRelatedOpinionRepository;
    private final SynthesisConsensusEvidenceRepository consensusEvidenceRepository;

    public SynthesisSetQueryService(
            SynthesisSetRepository synthesisSetRepository,
            IssueAnswerRepository issueAnswerRepository,
            SynthesisInheritedGapAnswerRepository inheritedGapAnswerRepository,
            IssueRepository issueRepository,
            IssueDecisionRepository issueDecisionRepository,
            IssueRelatedOpinionRepository issueRelatedOpinionRepository,
            SynthesisConsensusEvidenceRepository consensusEvidenceRepository
    ) {
        this.synthesisSetRepository = synthesisSetRepository;
        this.issueAnswerRepository = issueAnswerRepository;
        this.inheritedGapAnswerRepository = inheritedGapAnswerRepository;
        this.issueRepository = issueRepository;
        this.issueDecisionRepository = issueDecisionRepository;
        this.issueRelatedOpinionRepository = issueRelatedOpinionRepository;
        this.consensusEvidenceRepository = consensusEvidenceRepository;
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
        SynthesisSet current = requireCurrent(sectionAccess);
        List<GapAnswerContext> sorted =
                loadValidatedGapAnswers(current, sectionAccess.sectionId()).values().stream()
                        .map(this::toContext)
                        .sorted(Comparator.comparing(GapAnswerContext::answeredAt)
                                .thenComparing(GapAnswerContext::answerId))
                        .toList();
        return new CurrentSynthesisContext(
                current.getId(),
                current.getOpinionGateGeneration(),
                current.getConsensusSummary(),
                sorted
        );
    }

    /** 초안 생성용 current set의 결정·GAP·aggregate 의견 근거까지 함께 반환한다. */
    public CurrentSynthesisContext getCurrentForDraftGeneration(
            VerifiedSectionAccess sectionAccess
    ) {
        SynthesisSet current = requireCurrent(sectionAccess);
        Long sectionId = sectionAccess.sectionId();
        List<GapAnswerContext> sorted = loadValidatedGapAnswers(current, sectionId).values().stream()
                .map(this::toContext)
                .sorted(Comparator.comparing(GapAnswerContext::answeredAt)
                        .thenComparing(GapAnswerContext::answerId))
                .toList();
        List<Issue> issues = issueRepository.findAllBySynthesisSet_IdOrderBySortOrderAsc(current.getId());
        Map<Long, IssueDecision> decisionByIssueId = issueDecisionRepository
                .findAllWithIssueBySynthesisSetId(current.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        decision -> decision.getIssue().getId(),
                        decision -> decision
                ));
        List<IssueRelatedOpinion> relatedOpinions =
                issueRelatedOpinionRepository.findAllWithIssueBySynthesisSetId(current.getId());
        Map<Long, List<Long>> evidenceIdsByIssue = issueEvidenceIds(relatedOpinions);

        List<ConflictDecisionContext> decisions = new ArrayList<>();
        List<GapIssueContext> gapIssues = new ArrayList<>();
        boolean hasUnresolvedConflict = false;
        for (Issue issue : issues) {
            validateIssue(issue, current.getId());
            List<Long> evidenceIds = evidenceIdsByIssue.getOrDefault(issue.getId(), List.of());
            if (issue.getType() == IssueType.CONFLICT) {
                IssueDecision decision = decisionByIssueId.get(issue.getId());
                if (issue.getStatus() != IssueStatus.RESOLVED || decision == null) {
                    hasUnresolvedConflict = true;
                    continue;
                }
                decisions.add(toDecisionContext(issue, decision, evidenceIds));
            } else {
                gapIssues.add(new GapIssueContext(
                        issue.getId(),
                        issue.getDescription(),
                        issue.getStatus() == IssueStatus.RESOLVED,
                        evidenceIds
                ));
            }
        }
        return new CurrentSynthesisContext(
                current.getId(),
                current.getOpinionGateGeneration(),
                current.getConsensusSummary(),
                sorted,
                opinionEvidence(current.getId(), relatedOpinions),
                decisions,
                gapIssues,
                hasUnresolvedConflict);
    }

    private SynthesisSet requireCurrent(VerifiedSectionAccess sectionAccess) {
        if (sectionAccess == null) {
            throw new IllegalArgumentException("검증된 section 접근 정보는 필수입니다.");
        }
        Long sectionId = sectionAccess.sectionId();
        return synthesisSetRepository
                .findTopByProjectSectionIdOrderByCreatedAtDescIdDesc(sectionId)
                .orElseThrow(() -> new IllegalStateException(
                        "AI context에 필요한 current synthesis set이 없습니다."));
    }

    private Map<Long, GapAnswerSource> loadValidatedGapAnswers(
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

        Map<Long, GapAnswerSource> unique = new HashMap<>();
        for (IssueAnswer answer : direct) {
            validateDirectAnswer(answer, currentSetId, expectedSectionId);
            unique.put(answer.getId(), new GapAnswerSource(answer, false));
        }
        for (SynthesisInheritedGapAnswer reference : inherited) {
            IssueAnswer answer = sourceById.get(reference.getSourceAnswerId());
            validateInheritedAnswer(
                    answer,
                    expectedSectionId,
                    reference.getSourceIssueId());
            unique.putIfAbsent(answer.getId(), new GapAnswerSource(answer, true));
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

    private GapAnswerContext toContext(GapAnswerSource source) {
        IssueAnswer answer = source.answer();
        return new GapAnswerContext(
                answer.getIssue().getId(),
                answer.getId(),
                answer.getContent(),
                answer.getAnsweredAt(),
                answer.getAuthorNameSnapshot(),
                source.inherited());
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
                .map(GapAnswerSource::answer)
                .map(answer -> new GapAnswerInputView(
                        answer.getId(),
                        answer.getIssue().getId(),
                        answer.getAuthorNameSnapshot(),
                        answer.getContent()))
                .sorted(Comparator.comparing(GapAnswerInputView::answerId))
                .toList();
    }

    private Map<Long, List<Long>> issueEvidenceIds(List<IssueRelatedOpinion> relatedOpinions) {
        Map<Long, Set<Long>> unique = new HashMap<>();
        for (IssueRelatedOpinion evidence : relatedOpinions) {
            if (evidence == null || evidence.getIssue() == null || evidence.getIssue().getId() == null
                    || evidence.getOpinionId() == null) {
                throw new IllegalStateException("쟁점 의견 근거의 무결성이 깨졌습니다.");
            }
            unique.computeIfAbsent(evidence.getIssue().getId(), ignored -> new TreeSet<>())
                    .add(evidence.getOpinionId());
        }
        Map<Long, List<Long>> result = new HashMap<>();
        unique.forEach((issueId, opinionIds) ->
                result.put(issueId, List.copyOf(opinionIds)));
        return result;
    }

    private List<SynthesisOpinionEvidenceContext> opinionEvidence(
            Long synthesisSetId,
            List<IssueRelatedOpinion> relatedOpinions
    ) {
        Map<Long, SynthesisOpinionEvidenceContext> unique = new LinkedHashMap<>();
        for (SynthesisConsensusEvidence evidence :
                consensusEvidenceRepository.findAllBySynthesisSet_IdOrderBySortOrderAsc(synthesisSetId)) {
            addOpinionEvidence(unique, evidence.getOpinionId(),
                    evidence.getAuthorNameSnapshot(), evidence.getExcerpt());
        }
        for (IssueRelatedOpinion evidence : relatedOpinions) {
            addOpinionEvidence(unique, evidence.getOpinionId(),
                    evidence.getAuthorNameSnapshot(), evidence.getExcerpt());
        }
        return unique.values().stream()
                .sorted(Comparator.comparing(SynthesisOpinionEvidenceContext::opinionId))
                .toList();
    }

    private void addOpinionEvidence(
            Map<Long, SynthesisOpinionEvidenceContext> unique,
            Long opinionId,
            String authorName,
            String content
    ) {
        if (opinionId == null || authorName == null || authorName.isBlank()
                || content == null || content.isBlank()) {
            throw new IllegalStateException("synthesis 의견 근거의 무결성이 깨졌습니다.");
        }
        unique.putIfAbsent(opinionId,
                new SynthesisOpinionEvidenceContext(opinionId, authorName, content));
    }

    private void validateIssue(Issue issue, Long expectedSetId) {
        if (issue == null || issue.getId() == null || issue.getSynthesisSet() == null
                || !expectedSetId.equals(issue.getSynthesisSet().getId())
                || issue.getType() == null || issue.getStatus() == null
                || issue.getDescription() == null || issue.getDescription().isBlank()) {
            throw new IllegalStateException("current synthesis 쟁점의 무결성이 깨졌습니다.");
        }
    }

    private ConflictDecisionContext toDecisionContext(
            Issue issue,
            IssueDecision decision,
            List<Long> evidenceOpinionIds
    ) {
        String decisionText = decision.getSelectedOption() == null
                ? decision.getCustomInput()
                : decision.getSelectedOption().getOptionText();
        if (decision.getId() == null || decisionText == null || decisionText.isBlank()) {
            throw new IllegalStateException("CONFLICT 결정의 무결성이 깨졌습니다.");
        }
        return new ConflictDecisionContext(
                issue.getId(),
                decision.getId(),
                issue.getDescription(),
                issue.getQuestion(),
                decisionText,
                evidenceOpinionIds
        );
    }

    private record GapAnswerSource(IssueAnswer answer, boolean inherited) {
    }
}
