package com.wevo.backend.issue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wevo.backend.issue.domain.Issue;
import com.wevo.backend.issue.domain.IssueAnswer;
import com.wevo.backend.issue.domain.IssueType;
import com.wevo.backend.issue.domain.SynthesisInheritedGapAnswer;
import com.wevo.backend.issue.domain.SynthesisSet;
import com.wevo.backend.issue.repository.IssueAnswerRepository;
import com.wevo.backend.issue.repository.SynthesisInheritedGapAnswerRepository;
import com.wevo.backend.issue.repository.SynthesisSetRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SynthesisSetQueryServiceTest {

    private static final Long SECTION_ID = 10L;
    private static final Long SET_ID = 20L;

    @Mock private SynthesisSetRepository synthesisSetRepository;
    @Mock private IssueAnswerRepository issueAnswerRepository;
    @Mock private SynthesisInheritedGapAnswerRepository inheritedGapAnswerRepository;
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
                inheritedGapAnswerRepository);
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
}
