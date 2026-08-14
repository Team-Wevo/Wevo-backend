package com.wevo.backend.issue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.wevo.backend.issue.domain.Issue;
import com.wevo.backend.issue.domain.IssueAnswer;
import com.wevo.backend.issue.domain.IssueDecision;
import com.wevo.backend.issue.domain.IssueRelatedOpinion;
import com.wevo.backend.issue.domain.IssueStatus;
import com.wevo.backend.issue.domain.IssueType;
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
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class SynthesisSetQueryServiceTest {

    private static final Long SECTION_ID = 10L;
    private static final Long SET_ID = 20L;

    @Mock private SynthesisSetRepository synthesisSetRepository;
    @Mock private IssueAnswerRepository issueAnswerRepository;
    @Mock private SynthesisInheritedGapAnswerRepository inheritedGapAnswerRepository;
    @Mock private IssueRepository issueRepository;
    @Mock private IssueDecisionRepository issueDecisionRepository;
    @Mock private IssueRelatedOpinionRepository issueRelatedOpinionRepository;
    @Mock private SynthesisConsensusEvidenceRepository consensusEvidenceRepository;
    @Mock private SynthesisSet currentSet;
    @Mock private SynthesisInheritedGapAnswer inheritedReference;
    @Mock private IssueAnswer sourceAnswer;
    @Mock private Issue sourceIssue;
    @Mock private SynthesisSet sourceSet;

    private SynthesisSetQueryService service;

    @BeforeEach
    void setUp() {
        service = new SynthesisSetQueryService(
                synthesisSetRepository,
                issueAnswerRepository,
                inheritedGapAnswerRepository,
                issueRepository,
                issueDecisionRepository,
                issueRelatedOpinionRepository,
                consensusEvidenceRepository);
    }

    @Test
    @DisplayName("승계 GAP 원본이 누락되면 조용히 제외하지 않고 실패한다")
    void findCurrentGapAnswerInput_missingInheritedSource_fails() {
        givenCurrentSet();
        givenInheritedReference(100L, 200L);
        given(issueAnswerRepository.findAllWithIssueBySynthesisSetId(SET_ID))
                .willReturn(List.of());
        given(inheritedGapAnswerRepository.findAllBySynthesisSet_Id(SET_ID))
                .willReturn(List.of(inheritedReference));
        given(issueAnswerRepository.findAllWithIssueByIdIn(List.of(100L)))
                .willReturn(List.of());

        assertThatThrownBy(() -> service.findCurrentGapAnswerInput(SECTION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("원본 참조가 누락");

        verify(issueAnswerRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("승계 참조의 sourceIssueId가 실제 답변 쟁점과 다르면 실패한다")
    void findCurrentGapAnswerInput_mismatchedSourceIssue_fails() {
        givenCurrentSet();
        givenInheritedReference(100L, 200L);
        givenValidSourceAnswer(100L, 201L);
        given(issueAnswerRepository.findAllWithIssueBySynthesisSetId(SET_ID))
                .willReturn(List.of());
        given(inheritedGapAnswerRepository.findAllBySynthesisSet_Id(SET_ID))
                .willReturn(List.of(inheritedReference));
        given(issueAnswerRepository.findAllWithIssueByIdIn(List.of(100L)))
                .willReturn(List.of(sourceAnswer));

        assertThatThrownBy(() -> service.findCurrentGapAnswerInput(SECTION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("무결성이 깨졌습니다");

        verify(issueAnswerRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("유효한 승계 GAP 원본은 일괄 조회해 입력으로 반환한다")
    void findCurrentGapAnswerInput_validInheritedSource_returnsView() {
        givenCurrentSet();
        givenInheritedReference(100L, 200L);
        givenValidSourceAnswer(100L, 200L);
        given(sourceAnswer.getAuthorNameSnapshot()).willReturn("윤호");
        given(issueAnswerRepository.findAllWithIssueBySynthesisSetId(SET_ID))
                .willReturn(List.of());
        given(inheritedGapAnswerRepository.findAllBySynthesisSet_Id(SET_ID))
                .willReturn(List.of(inheritedReference));
        given(issueAnswerRepository.findAllWithIssueByIdIn(List.of(100L)))
                .willReturn(List.of(sourceAnswer));

        List<GapAnswerInputView> result = service.findCurrentGapAnswerInput(SECTION_ID);

        assertThat(result).containsExactly(
                new GapAnswerInputView(100L, 200L, "윤호", "추가 근거"));
        verify(issueAnswerRepository).findAllWithIssueByIdIn(List.of(100L));
        verify(issueAnswerRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("초안 생성 조회는 current CONFLICT 결정 본문을 포함하고 미결 여부를 계산한다")
    void getCurrentForDraftGeneration_includesResolvedDecisionAndDetectsUnresolved() {
        VerifiedSectionAccess access = mock(VerifiedSectionAccess.class);
        Issue resolved = mock(Issue.class);
        Issue unresolved = mock(Issue.class);
        IssueDecision decision = mock(IssueDecision.class);
        IssueRelatedOpinion relatedOpinion = mock(IssueRelatedOpinion.class);
        given(access.sectionId()).willReturn(SECTION_ID);
        givenCurrentSet();
        given(currentSet.getOpinionGateGeneration()).willReturn(3L);
        given(currentSet.getConsensusSummary()).willReturn("합의");
        given(issueAnswerRepository.findAllWithIssueBySynthesisSetId(SET_ID))
                .willReturn(List.of());
        given(inheritedGapAnswerRepository.findAllBySynthesisSet_Id(SET_ID))
                .willReturn(List.of());
        given(issueRepository.findAllBySynthesisSet_IdOrderBySortOrderAsc(SET_ID))
                .willReturn(List.of(resolved, unresolved));
        givenIssue(resolved, 31L, IssueStatus.RESOLVED, "결정된 충돌", "결정 질문");
        givenIssue(unresolved, 32L, IssueStatus.PENDING, "미결 충돌", "미결 질문");
        given(decision.getId()).willReturn(41L);
        given(decision.getIssue()).willReturn(resolved);
        given(decision.getCustomInput()).willReturn("OWNER 직접 결정");
        given(issueDecisionRepository.findAllWithIssueBySynthesisSetId(SET_ID))
                .willReturn(List.of(decision));
        given(relatedOpinion.getIssue()).willReturn(resolved);
        given(relatedOpinion.getOpinionId()).willReturn(51L);
        given(relatedOpinion.getAuthorNameSnapshot()).willReturn("팀원");
        given(relatedOpinion.getExcerpt()).willReturn("결정 근거");
        given(issueRelatedOpinionRepository.findAllWithIssueBySynthesisSetId(SET_ID))
                .willReturn(List.of(relatedOpinion, relatedOpinion));
        given(consensusEvidenceRepository.findAllBySynthesisSet_IdOrderBySortOrderAsc(SET_ID))
                .willReturn(List.of());

        CurrentSynthesisContext result = service.getCurrentForDraftGeneration(access);

        assertThat(result.hasUnresolvedConflict()).isTrue();
        assertThat(result.conflictDecisions()).singleElement()
                .satisfies(item -> {
                    assertThat(item.issueId()).isEqualTo(31L);
                    assertThat(item.decisionId()).isEqualTo(41L);
                    assertThat(item.decision()).isEqualTo("OWNER 직접 결정");
                    assertThat(item.evidenceOpinionIds()).containsExactly(51L);
                });
        assertThat(result.opinionEvidence()).singleElement()
                .satisfies(item -> assertThat(item.opinionId()).isEqualTo(51L));
        verify(issueRelatedOpinionRepository, times(1))
                .findAllWithIssueBySynthesisSetId(SET_ID);
    }

    @Test
    @DisplayName("AI current set 다중 조회는 독립 REPEATABLE_READ 트랜잭션을 강제한다")
    void currentSetAssembliesDeclareIndependentRepeatableReadTransaction()
            throws NoSuchMethodException {
        for (String methodName : List.of(
                "getCurrentForAiContext",
                "getCurrentForDraftGeneration")) {
            Method method = SynthesisSetQueryService.class
                    .getMethod(methodName, VerifiedSectionAccess.class);
            Transactional transactional = method.getAnnotation(Transactional.class);

            assertThat(transactional).isNotNull();
            assertThat(transactional.readOnly()).isTrue();
            assertThat(transactional.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
            assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        }
    }

    private void givenCurrentSet() {
        given(synthesisSetRepository
                .findTopByProjectSectionIdOrderByCreatedAtDescIdDesc(SECTION_ID))
                .willReturn(Optional.of(currentSet));
        given(currentSet.getId()).willReturn(SET_ID);
        given(currentSet.getProjectSectionId()).willReturn(SECTION_ID);
    }

    private void givenInheritedReference(Long sourceAnswerId, Long sourceIssueId) {
        given(inheritedReference.getSynthesisSet()).willReturn(currentSet);
        given(inheritedReference.getSourceAnswerId()).willReturn(sourceAnswerId);
        given(inheritedReference.getSourceIssueId()).willReturn(sourceIssueId);
    }

    private void givenValidSourceAnswer(Long answerId, Long issueId) {
        given(sourceAnswer.getId()).willReturn(answerId);
        given(sourceAnswer.getIssue()).willReturn(sourceIssue);
        given(sourceAnswer.getContent()).willReturn("추가 근거");
        given(sourceAnswer.getAnsweredAt()).willReturn(LocalDateTime.of(2026, 7, 25, 15, 0));
        given(sourceIssue.getId()).willReturn(issueId);
        given(sourceIssue.getType()).willReturn(IssueType.GAP);
        given(sourceIssue.getSynthesisSet()).willReturn(sourceSet);
        given(sourceSet.getProjectSectionId()).willReturn(SECTION_ID);
    }

    private void givenIssue(
            Issue issue,
            Long issueId,
            IssueStatus status,
            String description,
            String question
    ) {
        given(issue.getId()).willReturn(issueId);
        given(issue.getSynthesisSet()).willReturn(currentSet);
        given(issue.getType()).willReturn(IssueType.CONFLICT);
        given(issue.getStatus()).willReturn(status);
        given(issue.getDescription()).willReturn(description);
        if (status == IssueStatus.RESOLVED) {
            given(issue.getQuestion()).willReturn(question);
        }
    }
}
