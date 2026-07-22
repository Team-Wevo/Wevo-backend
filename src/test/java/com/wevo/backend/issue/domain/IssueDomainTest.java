package com.wevo.backend.issue.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IssueDomainTest {

    @Test
    @DisplayName("CONFLICT에는 질문이 필요하고 GAP에는 질문을 저장할 수 없다")
    void issue_enforcesTypeSpecificQuestion() {
        SynthesisSet set = synthesisSet();

        assertThatIllegalArgumentException().isThrownBy(() -> Issue.builder()
                .synthesisSet(set).type(IssueType.CONFLICT)
                .description("방식 충돌").sortOrder(1).build());
        assertThatIllegalArgumentException().isThrownBy(() -> Issue.builder()
                .synthesisSet(set).type(IssueType.GAP)
                .description("근거 부족").question("질문").sortOrder(1).build());
    }

    @Test
    @DisplayName("선택지는 CONFLICT 쟁점에만 생성할 수 있다")
    void option_rejectsGapIssue() {
        Issue gap = Issue.builder()
                .synthesisSet(synthesisSet()).type(IssueType.GAP)
                .description("근거 부족").sortOrder(1).build();

        assertThatIllegalArgumentException().isThrownBy(() -> IssueOption.builder()
                .issue(gap).optionText("허용되지 않는 선택지").sortOrder(1).build());
    }

    @Test
    @DisplayName("결정은 CONFLICT에 선택지 또는 직접 입력 하나만 저장한다")
    void decision_acceptsExactlyOneChoiceForConflict() {
        Issue conflict = conflict();
        IssueOption option = IssueOption.builder()
                .issue(conflict).optionText("첫 번째 안").sortOrder(1).build();
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);

        IssueDecision selected = IssueDecision.select(conflict, 1L, option, now);
        IssueDecision custom = IssueDecision.custom(conflict, 1L, "두 안을 절충", now);

        assertThat(selected.getSelectedOption()).isEqualTo(option);
        assertThat(selected.getCustomInput()).isNull();
        assertThat(custom.getSelectedOption()).isNull();
        assertThat(custom.getCustomInput()).isEqualTo("두 안을 절충");
        assertThatIllegalArgumentException().isThrownBy(() ->
                IssueDecision.custom(conflict, 1L, "가".repeat(201), now));
    }

    @Test
    @DisplayName("GAP 답변은 요청에 지목된 사용자만 작성할 수 있다")
    void answer_requiresTargetUser() {
        Issue gap = Issue.builder()
                .synthesisSet(synthesisSet()).type(IssueType.GAP)
                .description("시장 규모 근거 부족").sortOrder(1).build();
        EvidenceRequest request = EvidenceRequest.builder()
                .issue(gap).requestedByUserId(1L).targetUserId(2L)
                .requestedAt(LocalDateTime.of(2026, 7, 22, 12, 0)).build();

        assertThatIllegalArgumentException().isThrownBy(() -> IssueAnswer.builder()
                .evidenceRequest(request).authorUserId(3L).authorNameSnapshot("팀원")
                .content("보충 근거").answeredAt(LocalDateTime.of(2026, 7, 22, 12, 5)).build());
    }

    private Issue conflict() {
        return Issue.builder()
                .synthesisSet(synthesisSet()).type(IssueType.CONFLICT)
                .description("타깃 범위가 다름").question("어느 범위를 선택할까요?")
                .sortOrder(1).build();
    }

    private SynthesisSet synthesisSet() {
        return SynthesisSet.builder()
                .requestId(UUID.randomUUID()).projectSectionId(10L)
                .opinionGateGeneration(0).consensusSummary("핵심 타깃에 합의했습니다.").build();
    }
}
