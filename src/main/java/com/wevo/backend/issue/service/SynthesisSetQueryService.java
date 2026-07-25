package com.wevo.backend.issue.service;

import com.wevo.backend.issue.domain.IssueAnswer;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.domain.SynthesisInheritedGapAnswer;
import com.wevo.backend.issue.domain.SynthesisSet;
import com.wevo.backend.issue.repository.IssueAnswerRepository;
import com.wevo.backend.issue.repository.SynthesisInheritedGapAnswerRepository;
import com.wevo.backend.issue.repository.SynthesisSetRepository;
import com.wevo.backend.project.service.VerifiedSectionAccess;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        if (!sectionId.equals(current.getProjectSectionId())) {
            throw new IllegalStateException("current synthesis set의 section 소속이 일치하지 않습니다.");
        }

        List<IssueAnswer> direct =
                issueAnswerRepository.findAllWithIssueBySynthesisSetId(current.getId());
        List<SynthesisInheritedGapAnswer> inherited =
                inheritedGapAnswerRepository.findAllBySynthesisSet_Id(current.getId());
        List<Long> inheritedIds = inherited.stream()
                .map(SynthesisInheritedGapAnswer::getSourceAnswerId)
                .distinct()
                .toList();
        List<IssueAnswer> sources = inheritedIds.isEmpty()
                ? List.of()
                : issueAnswerRepository.findAllWithIssueByIdIn(inheritedIds);

        Map<Long, IssueAnswer> sourceById = new HashMap<>();
        sources.forEach(answer -> sourceById.put(answer.getId(), answer));
        if (sourceById.size() != inheritedIds.size()) {
            throw new IllegalStateException("승계 GAP 답변의 원본 참조가 누락되었습니다.");
        }

        Map<Long, GapAnswerContext> unique = new HashMap<>();
        for (IssueAnswer answer : direct) {
            validateDirectAnswer(answer, current.getId(), sectionId);
            unique.put(answer.getId(), toContext(answer));
        }
        for (SynthesisInheritedGapAnswer reference : inherited) {
            if (!current.getId().equals(reference.getSynthesisSet().getId())) {
                throw new IllegalStateException("승계 GAP 답변이 다른 synthesis set에 속합니다.");
            }
            IssueAnswer answer = sourceById.get(reference.getSourceAnswerId());
            validateInheritedAnswer(answer, sectionId, reference.getSourceIssueId());
            unique.putIfAbsent(answer.getId(), toContext(answer));
        }

        List<GapAnswerContext> sorted = new ArrayList<>(unique.values());
        sorted.sort(Comparator.comparing(GapAnswerContext::answeredAt)
                .thenComparing(GapAnswerContext::answerId));
        return new CurrentSynthesisContext(
                current.getId(),
                current.getOpinionGateGeneration(),
                current.getConsensusSummary(),
                sorted);
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
}
