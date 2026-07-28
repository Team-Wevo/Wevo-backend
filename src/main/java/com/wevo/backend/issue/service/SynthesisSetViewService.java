package com.wevo.backend.issue.service;

import com.wevo.backend.issue.domain.Issue;
import com.wevo.backend.issue.domain.IssueAnswer;
import com.wevo.backend.issue.domain.IssueDecision;
import com.wevo.backend.issue.domain.IssueOption;
import com.wevo.backend.issue.domain.IssueRelatedOpinion;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.domain.SynthesisInheritedGapAnswer;
import com.wevo.backend.issue.domain.SynthesisSet;
import com.wevo.backend.issue.repository.EvidenceRequestRepository;
import com.wevo.backend.issue.repository.IssueAnswerRepository;
import com.wevo.backend.issue.repository.IssueDecisionRepository;
import com.wevo.backend.issue.repository.IssueOptionRepository;
import com.wevo.backend.issue.repository.IssueRelatedOpinionRepository;
import com.wevo.backend.issue.repository.IssueRepository;
import com.wevo.backend.issue.repository.SynthesisInheritedGapAnswerRepository;
import com.wevo.backend.issue.repository.SynthesisSetRepository;
import com.wevo.backend.issue.service.SynthesisSetView.AnswerView;
import com.wevo.backend.issue.service.SynthesisSetView.DecisionView;
import com.wevo.backend.issue.service.SynthesisSetView.InheritedGapAnswerView;
import com.wevo.backend.issue.service.SynthesisSetView.IssueView;
import com.wevo.backend.issue.service.SynthesisSetView.RelatedOpinionView;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정리 세트 <b>한 개의 상세</b>를 조회용 표현으로 공개하는 issue 도메인의 읽기 경계. (API_SPEC §3.8.2)
 *
 * <p>AI 입력용 경계는 {@link SynthesisSetQueryService}가 담당한다 — 그쪽은 "재정리 입력에 넣을
 * GAP 답변 집합"을 만들고, 이 서비스는 "화면에 그릴 현재 세트"를 만든다. 관심사가 달라 분리했다.
 *
 * <p><b>어느 세트를 볼지 고르지 않는다</b> — 현재 세트 판정(최신 성공 실행의 {@code resultId})은
 * AI 작업 이력을 아는 ai 도메인의 책임이고, 이 서비스는 지정된 세트를 그대로 조립한다.
 *
 * <p>쟁점 수만큼 쿼리가 늘지 않도록 하위 데이터(선택지·관련 의견·결정·근거 요청·답변)는
 * <b>세트 단위로 한 번에</b> 읽고 메모리에서 쟁점별로 묶는다.
 */
@Service
@Transactional(readOnly = true)
public class SynthesisSetViewService {

    private final SynthesisSetRepository synthesisSetRepository;
    private final IssueRepository issueRepository;
    private final IssueOptionRepository issueOptionRepository;
    private final IssueRelatedOpinionRepository issueRelatedOpinionRepository;
    private final IssueDecisionRepository issueDecisionRepository;
    private final EvidenceRequestRepository evidenceRequestRepository;
    private final IssueAnswerRepository issueAnswerRepository;
    private final SynthesisInheritedGapAnswerRepository inheritedGapAnswerRepository;

    public SynthesisSetViewService(
            SynthesisSetRepository synthesisSetRepository,
            IssueRepository issueRepository,
            IssueOptionRepository issueOptionRepository,
            IssueRelatedOpinionRepository issueRelatedOpinionRepository,
            IssueDecisionRepository issueDecisionRepository,
            EvidenceRequestRepository evidenceRequestRepository,
            IssueAnswerRepository issueAnswerRepository,
            SynthesisInheritedGapAnswerRepository inheritedGapAnswerRepository
    ) {
        this.synthesisSetRepository = synthesisSetRepository;
        this.issueRepository = issueRepository;
        this.issueOptionRepository = issueOptionRepository;
        this.issueRelatedOpinionRepository = issueRelatedOpinionRepository;
        this.issueDecisionRepository = issueDecisionRepository;
        this.evidenceRequestRepository = evidenceRequestRepository;
        this.issueAnswerRepository = issueAnswerRepository;
        this.inheritedGapAnswerRepository = inheritedGapAnswerRepository;
    }

    /**
     * 지정한 정리 세트의 합의점·쟁점 상세를 조립한다.
     *
     * <p>세트가 없거나 다른 섹션 소속이면 <b>빈 값으로 바꾸지 않고</b> 실패한다 — 성공한 AI 작업의
     * {@code resultId}로만 호출되므로, 어긋난 참조는 데이터 무결성이 깨진 상태이지 사용자 오류가 아니다.
     *
     * <p><b>호출 규약 — 스냅샷이 고정된 트랜잭션에서 호출할 것.</b> 하위 데이터를 여러 쿼리로 읽어
     * 조립하므로, 문장마다 스냅샷을 새로 잡는 기본 격리(READ COMMITTED)에서는 동시 결정·답변 커밋이
     * 끼어 {@code status=RESOLVED}인데 {@code decision}이 비는 자체 모순 결과가 나올 수 있다.
     * 스냅샷 경계는 <b>호출자</b>가 소유한다 — 여기서 격리 수준을 선언해도 이미 열린 트랜잭션에
     * 참여할 때는 무시되기 때문이다. (진입점: {@code SynthesisQueryService#getSynthesis}가
     * {@code REPEATABLE_READ}로 고정한다)
     *
     * @param projectSectionId 세트가 속해야 하는 섹션 (호출자가 접근 권한을 검증한 섹션)
     * @param synthesisSetId   조립할 세트의 id (= 성공한 AI 작업의 {@code resultId})
     */
    public SynthesisSetView getSetView(Long projectSectionId, Long synthesisSetId) {
        Objects.requireNonNull(projectSectionId, "projectSectionId는 필수입니다.");
        Objects.requireNonNull(synthesisSetId, "synthesisSetId는 필수입니다.");
        SynthesisSet set = synthesisSetRepository.findById(synthesisSetId)
                .orElseThrow(() -> new IllegalStateException(
                        "AI 작업이 가리키는 정리 세트가 없습니다: " + synthesisSetId));
        if (!projectSectionId.equals(set.getProjectSectionId())) {
            throw new IllegalStateException("정리 세트의 section 소속이 일치하지 않습니다.");
        }

        Long setId = set.getId();
        Map<Long, List<String>> optionsByIssue = optionsByIssue(setId);
        Map<Long, List<RelatedOpinionView>> relatedByIssue = relatedOpinionsByIssue(setId);
        Map<Long, DecisionView> decisionByIssue = decisionsByIssue(setId);
        Set<Long> evidenceRequestedIssues = evidenceRequestedIssues(setId);
        Map<Long, AnswerView> answerByIssue = answersByIssue(setId);

        List<IssueView> issues = issueRepository
                .findAllBySynthesisSet_IdOrderBySortOrderAsc(setId).stream()
                .map(issue -> toIssueView(
                        issue,
                        relatedByIssue.getOrDefault(issue.getId(), List.of()),
                        optionsByIssue.getOrDefault(issue.getId(), List.of()),
                        decisionByIssue.get(issue.getId()),
                        evidenceRequestedIssues.contains(issue.getId()),
                        answerByIssue.get(issue.getId())))
                .toList();

        return new SynthesisSetView(
                set.getRequestId(),
                set.getConsensusSummary(),
                issues,
                inheritedGapAnswers(setId));
    }

    /**
     * 유형에 따라 채우는 필드를 가른다 — {@code CONFLICT}는 질문·선택지·결정, {@code GAP}은
     * 근거 요청 여부·답변만 갖는다. 해당 없는 필드는 {@code null}로 두어 응답에서 생략되게 한다.
     */
    private IssueView toIssueView(
            Issue issue,
            List<RelatedOpinionView> relatedOpinions,
            List<String> options,
            DecisionView decision,
            boolean evidenceRequested,
            AnswerView answer
    ) {
        boolean conflict = issue.getType() == IssueType.CONFLICT;
        return new IssueView(
                issue.getId(),
                issue.getType(),
                issue.getStatus(),
                issue.getDescription(),
                relatedOpinions,
                conflict ? issue.getQuestion() : null,
                conflict ? options : null,
                conflict ? decision : null,
                conflict ? null : evidenceRequested,
                conflict ? null : answer);
    }

    private Map<Long, List<String>> optionsByIssue(Long setId) {
        return issueOptionRepository.findAllWithIssueBySynthesisSetId(setId).stream()
                .collect(Collectors.groupingBy(
                        option -> option.getIssue().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(IssueOption::getOptionText, Collectors.toList())));
    }

    private Map<Long, List<RelatedOpinionView>> relatedOpinionsByIssue(Long setId) {
        return issueRelatedOpinionRepository.findAllWithIssueBySynthesisSetId(setId).stream()
                .collect(Collectors.groupingBy(
                        related -> related.getIssue().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(this::toRelatedOpinionView, Collectors.toList())));
    }

    private Map<Long, DecisionView> decisionsByIssue(Long setId) {
        return issueDecisionRepository.findAllWithIssueBySynthesisSetId(setId).stream()
                .collect(Collectors.toMap(
                        decision -> decision.getIssue().getId(),
                        this::toDecisionView));
    }

    private Set<Long> evidenceRequestedIssues(Long setId) {
        return evidenceRequestRepository.findAllWithIssueBySynthesisSetId(setId).stream()
                .map(request -> request.getIssue().getId())
                .collect(Collectors.toSet());
    }

    private Map<Long, AnswerView> answersByIssue(Long setId) {
        return issueAnswerRepository.findAllWithIssueBySynthesisSetId(setId).stream()
                .collect(Collectors.toMap(
                        answer -> answer.getIssue().getId(),
                        this::toAnswerView));
    }

    private List<InheritedGapAnswerView> inheritedGapAnswers(Long setId) {
        return inheritedGapAnswerRepository.findAllBySynthesisSet_Id(setId).stream()
                .sorted(Comparator.comparing(SynthesisInheritedGapAnswer::getSourceAnswerId))
                .map(reference -> new InheritedGapAnswerView(
                        reference.getSourceIssueId(), reference.getSourceAnswerId()))
                .toList();
    }

    private RelatedOpinionView toRelatedOpinionView(IssueRelatedOpinion related) {
        return new RelatedOpinionView(
                related.getOpinionId(),
                related.getAuthorUserId(),
                related.getAuthorNameSnapshot(),
                related.getExcerpt());
    }

    /** 선택지 결정이면 선택지 본문을, 직접 입력 결정이면 입력값을 담는다(둘 중 하나만 값이 있다). */
    private DecisionView toDecisionView(IssueDecision decision) {
        IssueOption selected = decision.getSelectedOption();
        return new DecisionView(
                selected == null ? null : selected.getOptionText(),
                decision.getCustomInput(),
                decision.getDecidedAt());
    }

    private AnswerView toAnswerView(IssueAnswer answer) {
        return new AnswerView(
                answer.getId(),
                answer.getAuthorNameSnapshot(),
                answer.getContent(),
                answer.getAnsweredAt());
    }
}
