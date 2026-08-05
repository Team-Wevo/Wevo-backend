package com.wevo.backend.ai.evaluation;

import com.wevo.backend.ai.context.AiBaseDraftContext;
import com.wevo.backend.ai.context.AiConflictDecisionContext;
import com.wevo.backend.ai.context.AiDraftSynthesisContext;
import com.wevo.backend.ai.context.AiGapIssueContext;
import com.wevo.backend.ai.context.AiOpinionContext;
import com.wevo.backend.ai.context.AiOpinionEvidenceContext;
import com.wevo.backend.ai.context.AiPrerequisiteContext;
import com.wevo.backend.ai.context.AiProjectBrief;
import com.wevo.backend.ai.context.AiProjectIdentity;
import com.wevo.backend.ai.context.AiSectionContext;
import com.wevo.backend.ai.context.AiTemplateContext;
import com.wevo.backend.ai.context.DraftGenerationContext;
import com.wevo.backend.ai.context.DraftReviewContext;
import com.wevo.backend.ai.context.IssueDetectionContext;
import com.wevo.backend.ai.context.AiInputSnapshotHasher;
import com.wevo.backend.ai.service.SynthesisPromptContext;
import com.wevo.backend.section.domain.ProjectSectionStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Provider 중립 fixture를 제품 prompt factory가 소비하는 synthetic context로 변환한다. */
final class SyntheticEvaluationContextFactory {

    private static final long PROJECT_ID = 9_001L;
    private static final long SECTION_ID = 9_101L;
    private static final long SYNTHESIS_SET_ID = 9_201L;
    private static final long GAP_ISSUE_ID = 9_301L;
    private static final long CONFLICT_ISSUE_ID = 9_302L;
    private static final long CONFLICT_DECISION_ID = 9_401L;
    private static final String SYNTHETIC_TIME = "2026-07-31T10:00:00";

    private final AiInputSnapshotHasher snapshotHasher = new AiInputSnapshotHasher();

    IssueDetectionContext issueDetection(AiEvaluationFixture fixture) {
        return new IssueDetectionContext(
                project(fixture), brief(fixture), section(fixture, ProjectSectionStatus.COLLECTING),
                opinions(fixture)
        );
    }

    SynthesisPromptContext synthesis(AiEvaluationFixture fixture) {
        return new SynthesisPromptContext(
                SynthesisPromptContext.PARTIAL,
                opinions(fixture),
                List.of(),
                List.of()
        );
    }

    DraftGenerationContext draftGeneration(AiEvaluationFixture fixture) {
        List<AiOpinionEvidenceContext> evidence = opinions(fixture).stream()
                .map(opinion -> new AiOpinionEvidenceContext(
                        opinion.opinionId(), opinion.submittedContent()))
                .toList();
        List<Long> evidenceIds = evidence.stream().map(AiOpinionEvidenceContext::opinionId).toList();
        boolean unresolvedGap = fixture.metadata().tags().contains("gap")
                || fixture.metadata().tags().contains("unresolved-gap");
        boolean conflict = fixture.metadata().tags().contains("conflict")
                || fixture.metadata().tags().contains("needs-decision");
        AiDraftSynthesisContext synthesis = new AiDraftSynthesisContext(
                SYNTHESIS_SET_ID,
                1,
                fixture.input().projectContext(),
                List.of(),
                evidence,
                conflict
                        ? List.of(new AiConflictDecisionContext(
                                CONFLICT_ISSUE_ID,
                                CONFLICT_DECISION_ID,
                                "fixture에서 합의된 선택을 반영한다.",
                                "어떤 방향을 선택할 것인가?",
                                "합성 fixture의 결정안을 따른다.",
                                evidenceIds
                        ))
                        : List.of(),
                unresolvedGap
                        ? List.of(new AiGapIssueContext(
                                GAP_ISSUE_ID,
                                "입력에 확정 근거가 없는 항목은 미확인으로 남긴다.",
                                false,
                                evidenceIds
                        ))
                        : List.of(),
                false
        );
        boolean stale = fixture.metadata().tags().contains("stale-input");
        return new DraftGenerationContext(
                project(fixture),
                brief(fixture),
                section(fixture, ProjectSectionStatus.SYNTHESIZING),
                synthesis,
                new AiBaseDraftContext(stale ? 2 : 0, stale ? fixture.input().sectionContext() : null),
                prerequisites(fixture)
        );
    }

    DraftReviewContext draftReview(AiEvaluationFixture fixture) {
        return new DraftReviewContext(
                project(fixture),
                section(fixture, ProjectSectionStatus.DRAFTING),
                9_501L,
                fixture.metadata().tags().contains("stale-input") ? 3 : 1,
                fixture.input().sectionContext(),
                prerequisites(fixture),
                snapshotHasher.hashCanonical(java.util.Arrays.asList(
                        fixture.metadata().id(), fixture.input().parentConfirmedContent()))
        );
    }

    Set<Long> allowedOpinionIds(AiEvaluationFixture fixture) {
        return Set.copyOf(idMap(fixture).externalToInternal().values());
    }

    String externalOpinionId(AiEvaluationFixture fixture, Long internalId) {
        String externalId = idMap(fixture).internalToExternal().get(internalId);
        return externalId == null ? "unknown-evidence-" + internalId : externalId;
    }

    private List<AiOpinionContext> opinions(AiEvaluationFixture fixture) {
        IdMap ids = idMap(fixture);
        return fixture.input().opinions().stream()
                .filter(AiEvaluationFixture.Opinion::submitted)
                .filter(opinion -> !opinion.deleted())
                .map(opinion -> new AiOpinionContext(
                        ids.externalToInternal().get(opinion.id()),
                        "synthetic-member-" + ids.externalToInternal().get(opinion.id()),
                        opinion.content(),
                        SYNTHETIC_TIME
                ))
                .toList();
    }

    private IdMap idMap(AiEvaluationFixture fixture) {
        Map<String, Long> forward = new LinkedHashMap<>();
        long next = 10_001L;
        for (AiEvaluationFixture.Opinion opinion : fixture.input().opinions()) {
            if (opinion.submitted() && !opinion.deleted()) {
                forward.put(opinion.id(), next++);
            }
        }
        Map<Long, String> reverse = new LinkedHashMap<>();
        forward.forEach((external, internal) -> reverse.put(internal, external));
        return new IdMap(Map.copyOf(forward), Map.copyOf(reverse));
    }

    private AiProjectIdentity project(AiEvaluationFixture fixture) {
        return new AiProjectIdentity(PROJECT_ID, "Synthetic Wevo Evaluation", fixture.input().outputType());
    }

    private AiProjectBrief brief(AiEvaluationFixture fixture) {
        return new AiProjectBrief(
                fixture.input().projectContext(),
                "합성 평가 데이터이며 실제 사용자 정보가 아니다.",
                "가상 프로젝트 팀"
        );
    }

    private AiSectionContext section(
            AiEvaluationFixture fixture,
            ProjectSectionStatus status
    ) {
        String guide = fixture.input().labels().isEmpty()
                ? "입력 근거와 미확정 항목을 구분한다."
                : String.join(", ", fixture.input().labels());
        return new AiSectionContext(
                SECTION_ID,
                "Synthetic evaluation section",
                1,
                status,
                1,
                fixture.metadata().tags().contains("stale-input"),
                new AiTemplateContext("synthetic-section", fixture.input().sectionContext(), guide)
        );
    }

    private List<AiPrerequisiteContext> prerequisites(AiEvaluationFixture fixture) {
        String parent = fixture.input().parentConfirmedContent();
        if (parent == null) {
            return List.of();
        }
        return List.of(new AiPrerequisiteContext(
                9_102L, "synthetic-parent", 0, 1, parent
        ));
    }

    private record IdMap(
            Map<String, Long> externalToInternal,
            Map<Long, String> internalToExternal
    ) {
    }
}
