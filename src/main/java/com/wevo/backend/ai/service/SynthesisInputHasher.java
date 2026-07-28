package com.wevo.backend.ai.service;

import com.wevo.backend.issue.service.GapAnswerInputView;
import com.wevo.backend.opinion.service.SubmittedOpinionView;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 의견 정리 입력 스냅샷을 정렬 고정한 canonical 형태로 직렬화해 SHA-256 해시(64자리 소문자 hex)를
 * 계산한다. (§3.8.1)
 *
 * <p>필드는 길이 접두어 인코딩으로 구분해 구분자 모호성을 없애고, 목록은 안정 키로 정렬해 조회
 * 순서에 해시가 흔들리지 않게 한다({@link AiJobIdempotencyKeyGenerator}와 동일한 규약).
 */
@Component
public class SynthesisInputHasher {

    private static final String CANONICAL_VERSION = "wevo-synthesis-input:v2";

    public String hash(SynthesisInputSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot은 필수입니다.");
        }

        MessageDigest digest = sha256();
        update(digest, CANONICAL_VERSION);
        update(digest, Long.toString(snapshot.opinionGateGeneration()));

        List<SubmittedOpinionView> opinions = snapshot.opinions().stream()
                .sorted(Comparator.comparing(SubmittedOpinionView::opinionId))
                .toList();
        update(digest, Integer.toString(opinions.size()));
        for (SubmittedOpinionView opinion : opinions) {
            update(digest, Long.toString(opinion.opinionId()));
            update(digest, Long.toString(opinion.authorId()));
            update(digest, opinion.submittedContent());
            update(digest, opinion.submittedAt().toString());
        }

        List<GapAnswerInputView> gapAnswers = snapshot.gapAnswers().stream()
                .sorted(Comparator.comparing(GapAnswerInputView::answerId))
                .toList();
        update(digest, Integer.toString(gapAnswers.size()));
        for (GapAnswerInputView answer : gapAnswers) {
            update(digest, Long.toString(answer.answerId()));
            update(digest, Long.toString(answer.sourceIssueId()));
            update(digest, answer.content());
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm을 사용할 수 없습니다.", exception);
        }
    }
}
