package com.wevo.backend.issue.service;

import com.wevo.backend.issue.domain.Issue;
import com.wevo.backend.issue.domain.IssueOption;
import com.wevo.backend.issue.domain.IssueRelatedOpinion;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.domain.SynthesisInheritedGapAnswer;
import com.wevo.backend.issue.domain.SynthesisSet;
import com.wevo.backend.issue.repository.IssueOptionRepository;
import com.wevo.backend.issue.repository.IssueRelatedOpinionRepository;
import com.wevo.backend.issue.repository.IssueRepository;
import com.wevo.backend.issue.repository.SynthesisInheritedGapAnswerRepository;
import com.wevo.backend.issue.repository.SynthesisSetRepository;
import com.wevo.backend.issue.service.SynthesisPersistCommand.InheritedGapAnswerRef;
import com.wevo.backend.issue.service.SynthesisPersistCommand.IssueSpec;
import com.wevo.backend.issue.service.SynthesisPersistCommand.RelatedOpinionSpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 성공한 AI 정리 결과를 정리 세트·쟁점 그래프로 저장하는 issue 도메인의 쓰기 경계. (§3.8.1)
 *
 * <p>이전 세트는 건드리지 않고 <b>새 세트를 추가</b>한다 — 대체(supersede)는 "최신 세트가 현재
 * 세트"라는 파생 규칙으로 이뤄지고, 과거 쟁점·결정·답변은 근거 이력으로 보존된다.
 */
@Service
public class SynthesisResultWriteService {

    private final SynthesisSetRepository synthesisSetRepository;
    private final IssueRepository issueRepository;
    private final IssueOptionRepository issueOptionRepository;
    private final IssueRelatedOpinionRepository issueRelatedOpinionRepository;
    private final SynthesisInheritedGapAnswerRepository inheritedGapAnswerRepository;

    public SynthesisResultWriteService(SynthesisSetRepository synthesisSetRepository,
                                       IssueRepository issueRepository,
                                       IssueOptionRepository issueOptionRepository,
                                       IssueRelatedOpinionRepository issueRelatedOpinionRepository,
                                       SynthesisInheritedGapAnswerRepository inheritedGapAnswerRepository) {
        this.synthesisSetRepository = synthesisSetRepository;
        this.issueRepository = issueRepository;
        this.issueOptionRepository = issueOptionRepository;
        this.issueRelatedOpinionRepository = issueRelatedOpinionRepository;
        this.inheritedGapAnswerRepository = inheritedGapAnswerRepository;
    }

    /**
     * 정리 세트와 하위 쟁점을 저장하고 새 세트의 id를 반환한다. AI 작업의 {@code resultId}로 연결된다.
     *
     * <p>AI 작업 완료 처리와 <b>같은 트랜잭션</b>에서만 실행한다({@link Propagation#MANDATORY}) —
     * 완료 시점 스냅샷 재대조를 통과한 경우에만 호출되므로, 저장과 작업 성공 확정이 원자적으로 함께
     * 커밋되거나 함께 롤백된다.
     *
     * @return 생성된 정리 세트 id
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long persist(SynthesisPersistCommand command) {
        SynthesisSet set = synthesisSetRepository.save(SynthesisSet.builder()
                .requestId(command.requestId())
                .projectSectionId(command.projectSectionId())
                .opinionGateGeneration(command.opinionGateGeneration())
                .consensusSummary(command.consensusSummary())
                .build());

        int issueOrder = 1;
        for (IssueSpec issueSpec : command.issues()) {
            Issue issue = issueRepository.save(Issue.builder()
                    .synthesisSet(set)
                    .type(issueSpec.type())
                    .description(issueSpec.description())
                    .question(issueSpec.question())
                    .sortOrder(issueOrder++)
                    .build());
            persistOptions(issue, issueSpec);
            persistRelatedOpinions(issue, issueSpec);
        }

        for (InheritedGapAnswerRef ref : command.inheritedGapAnswers()) {
            inheritedGapAnswerRepository.save(SynthesisInheritedGapAnswer.builder()
                    .synthesisSet(set)
                    .sourceIssueId(ref.sourceIssueId())
                    .sourceAnswerId(ref.sourceAnswerId())
                    .build());
        }

        return set.getId();
    }

    private void persistOptions(Issue issue, IssueSpec issueSpec) {
        if (issueSpec.type() != IssueType.CONFLICT) {
            return;
        }
        int optionOrder = 1;
        for (String optionText : issueSpec.options()) {
            issueOptionRepository.save(IssueOption.builder()
                    .issue(issue)
                    .optionText(optionText)
                    .sortOrder(optionOrder++)
                    .build());
        }
    }

    private void persistRelatedOpinions(Issue issue, IssueSpec issueSpec) {
        int relatedOrder = 1;
        for (RelatedOpinionSpec related : issueSpec.relatedOpinions()) {
            issueRelatedOpinionRepository.save(IssueRelatedOpinion.builder()
                    .issue(issue)
                    .opinionId(related.opinionId())
                    .authorUserId(related.authorUserId())
                    .authorNameSnapshot(related.authorName())
                    .excerpt(related.excerpt())
                    .sortOrder(relatedOrder++)
                    .build());
        }
    }
}
