package com.wevo.backend.ai.context;

import com.wevo.backend.ai.config.AiProperties;
import com.wevo.backend.project.domain.OutputType;
import com.wevo.backend.section.domain.ProjectSectionStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiInputSnapshotHasherTest {

    private final AiInputSnapshotHasher hasher = new AiInputSnapshotHasher();

    @Test
    void normalizesLineEndingsAndIncludesBudgetPolicyFingerprint() {
        IssueDetectionContext crlf = context("첫 줄\r\n둘째 줄");
        IssueDetectionContext lf = context("첫 줄\n둘째 줄");
        AiProperties smallBudget = AiTokenBudgetEstimatorTest.properties(100);
        AiProperties largerBudget = AiTokenBudgetEstimatorTest.properties(101);

        AiInputSnapshot first = hasher.snapshot(
                crlf,
                smallBudget.optionsFor(crlf.feature())
        );
        AiInputSnapshot normalized = hasher.snapshot(
                lf,
                smallBudget.optionsFor(lf.feature())
        );
        AiInputSnapshot changedPolicyFingerprint = hasher.snapshot(
                lf,
                largerBudget.optionsFor(lf.feature())
        );

        assertThat(first.canonicalBytes()).containsExactly(normalized.canonicalBytes());
        assertThat(first.inputSnapshotHash()).isEqualTo(normalized.inputSnapshotHash());
        assertThat(changedPolicyFingerprint.inputSnapshotHash())
                .isNotEqualTo(first.inputSnapshotHash());
        assertThat(first.inputSnapshotHash()).matches("^[0-9a-f]{64}$");
    }

    private IssueDetectionContext context(String content) {
        return new IssueDetectionContext(
                new AiProjectIdentity(1L, "프로젝트", OutputType.PRESENTATION),
                new AiProjectBrief(null, null, null),
                new AiSectionContext(
                        10L,
                        "문제 정의",
                        1,
                        ProjectSectionStatus.SYNTHESIZING,
                        2,
                        false,
                        new AiTemplateContext("problem-definition", null, null)
                ),
                List.of(new AiOpinionContext(
                        100L,
                        "member-1",
                        content,
                        "2026-07-24T10:00:00"
                ))
        );
    }
}
