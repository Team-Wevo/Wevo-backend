package com.wevo.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wevo.backend.issue.service.GapAnswerInputView;
import com.wevo.backend.opinion.service.SubmittedOpinionView;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SynthesisInputHasherTest {

    private final SynthesisInputHasher hasher = new SynthesisInputHasher();

    private SubmittedOpinionView opinion(long id, long authorId, String content) {
        return new SubmittedOpinionView(
                id,
                authorId,
                "작성자" + authorId,
                content,
                LocalDateTime.of(2026, 7, 28, 10, 0).plusMinutes(id)
        );
    }

    private GapAnswerInputView gapAnswer(long answerId, long issueId, String content) {
        return new GapAnswerInputView(answerId, issueId, "답변자", content);
    }

    @Test
    @DisplayName("해시는 64자리 소문자 SHA-256 hex다")
    void hash_is64LowerHex() {
        String hash = hasher.hash(new SynthesisInputSnapshot(
                List.of(opinion(1, 7, "의견 A")), List.of(), 0));

        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("같은 입력은 항상 같은 해시를 낸다")
    void hash_isDeterministic() {
        SynthesisInputSnapshot snapshot = new SynthesisInputSnapshot(
                List.of(opinion(1, 7, "의견 A"), opinion(2, 9, "의견 B")),
                List.of(gapAnswer(5, 3, "보충 근거")), 2);

        assertThat(hasher.hash(snapshot)).isEqualTo(hasher.hash(snapshot));
    }

    @Test
    @DisplayName("의견·답변의 조회 순서가 달라도 같은 해시다 (canonical 정렬)")
    void hash_isOrderIndependent() {
        String ascending = hasher.hash(new SynthesisInputSnapshot(
                List.of(opinion(1, 7, "의견 A"), opinion(2, 9, "의견 B")),
                List.of(gapAnswer(5, 3, "근거 1"), gapAnswer(8, 4, "근거 2")), 1));
        String reversed = hasher.hash(new SynthesisInputSnapshot(
                List.of(opinion(2, 9, "의견 B"), opinion(1, 7, "의견 A")),
                List.of(gapAnswer(8, 4, "근거 2"), gapAnswer(5, 3, "근거 1")), 1));

        assertThat(ascending).isEqualTo(reversed);
    }

    @Test
    @DisplayName("마감 세대가 바뀌면 해시가 달라진다 (의견이 같아도)")
    void hash_changesWithGateGeneration() {
        List<SubmittedOpinionView> opinions = List.of(opinion(1, 7, "의견 A"));

        String generationOne = hasher.hash(new SynthesisInputSnapshot(opinions, List.of(), 1));
        String generationTwo = hasher.hash(new SynthesisInputSnapshot(opinions, List.of(), 2));

        assertThat(generationOne).isNotEqualTo(generationTwo);
    }

    @Test
    @DisplayName("제출 본문이 바뀌면 해시가 달라진다")
    void hash_changesWithContent() {
        String before = hasher.hash(new SynthesisInputSnapshot(
                List.of(opinion(1, 7, "의견 A")), List.of(), 0));
        String after = hasher.hash(new SynthesisInputSnapshot(
                List.of(opinion(1, 7, "의견 A 수정")), List.of(), 0));

        assertThat(before).isNotEqualTo(after);
    }

    @Test
    @DisplayName("GAP 답변이 추가되면 해시가 달라진다")
    void hash_changesWithGapAnswers() {
        List<SubmittedOpinionView> opinions = List.of(opinion(1, 7, "의견 A"));

        String withoutAnswer = hasher.hash(new SynthesisInputSnapshot(opinions, List.of(), 0));
        String withAnswer = hasher.hash(new SynthesisInputSnapshot(
                opinions, List.of(gapAnswer(5, 3, "늦은 근거")), 0));

        assertThat(withoutAnswer).isNotEqualTo(withAnswer);
    }
}
